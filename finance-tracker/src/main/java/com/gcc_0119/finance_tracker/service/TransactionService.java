package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    public TransactionDTO createTransaction(String userId, TransactionDTO request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("交易金额必须为正数");
        }

        String fromAccountId = request.getFromAccountId();
        String toAccountId = request.getToAccountId();

        if (fromAccountId == null || fromAccountId.isBlank()
                || toAccountId == null || toAccountId.isBlank()) {
            throw new BusinessException("转出账户和转入账户不能为空");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessException("转出账户和转入账户不能相同");
        }

        BigDecimal amount = request.getAmount();

        accountRepository.findByIdAndUserId(fromAccountId, userId)
                .orElseThrow(() -> new BusinessException(404, "转出账户不存在或无权访问"));
        accountRepository.findByIdAndUserId(toAccountId, userId)
                .orElseThrow(() -> new BusinessException(404, "转入账户不存在或无权访问"));

        Account debited = debitAccount(fromAccountId, userId, amount);
        if (debited == null) {
            throw new BusinessException("余额不足");
        }

        Account credited = creditAccount(toAccountId, userId, amount);
        if (credited == null) {
            creditAccount(fromAccountId, userId, amount);
            throw new BusinessException(500, "转入账户操作失败，已回滚");
        }

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setFromAccountId(fromAccountId);
        transaction.setToAccountId(toAccountId);
        transaction.setAmount(amount);
        transaction.setDescription(request.getDescription());
        transaction.setTimestamp(LocalDateTime.now());

        Transaction saved = transactionRepository.save(transaction);
        return convertToDTO(saved);
    }

    public TransactionDTO reverseTransaction(String userId, String transactionId) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));

        if (!original.getUserId().equals(userId)) {
            throw new BusinessException(404, "交易记录不存在或无权访问");
        }
        if (original.isReversed()) {
            throw new BusinessException("该交易已被冲正，不能重复冲正");
        }

        BigDecimal amount = original.getAmount();

        Account refunded = creditAccount(original.getFromAccountId(), userId, amount);
        if (refunded == null) {
            throw new BusinessException(500, "冲正失败：退款操作异常");
        }

        Account debited = debitAccount(original.getToAccountId(), userId, amount);
        if (debited == null) {
            debitAccount(original.getFromAccountId(), userId, amount);
            throw new BusinessException("冲正失败：转入账户余额不足，已回滚");
        }

        original.setReversed(true);
        transactionRepository.save(original);

        Transaction reversal = new Transaction();
        reversal.setUserId(userId);
        reversal.setFromAccountId(original.getToAccountId());
        reversal.setToAccountId(original.getFromAccountId());
        reversal.setAmount(amount);
        reversal.setDescription("冲正: " + (original.getDescription() != null ? original.getDescription() : ""));
        reversal.setReversalOfId(transactionId);
        reversal.setTimestamp(LocalDateTime.now());

        Transaction savedReversal = transactionRepository.save(reversal);
        return convertToDTO(savedReversal);
    }

    public List<TransactionDTO> getAllTransactions(String userId) {
        return transactionRepository.findByUserId(userId).stream()
                .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Map<String, BigDecimal> getTotalByType(String userId, LocalDateTime start, LocalDateTime end) {
        MatchOperation matchUser = Aggregation.match(
                Criteria.where("userId").is(userId).and("timestamp").gte(start).lte(end));
        GroupOperation group = Aggregation.group("type").sum("amount").as("total");
        Aggregation agg = Aggregation.newAggregation(matchUser, group);

        List<Document> results = mongoTemplate.aggregate(agg, "transactions", Document.class).getMappedResults();

        return results.stream().collect(Collectors.toMap(
                r -> (String) r.get("_id"),
                r -> {
                    Object total = r.get("total");
                    BigDecimal val = BigDecimal.ZERO;
                    if (total != null) {
                        val = new BigDecimal(total.toString());
                    }
                    return val.setScale(2, RoundingMode.HALF_UP);
                }));
    }

    public Map<String, BigDecimal> getTotalByCategory(String userId, String type, LocalDateTime start,
            LocalDateTime end) {
        Criteria criteria = Criteria.where("userId").is(userId).and("timestamp").gte(start).lte(end);
        if (type != null && !type.isEmpty()) {
            criteria.and("type").is(type);
        }
        MatchOperation match = Aggregation.match(criteria);
        GroupOperation group = Aggregation.group("category").sum("amount").as("total");
        Aggregation agg = Aggregation.newAggregation(match, group);

        List<Document> results = mongoTemplate.aggregate(agg, "transactions", Document.class).getMappedResults();

        return results.stream().collect(Collectors.toMap(
                r -> (String) r.get("_id"),
                r -> {
                    Object total = r.get("total");
                    BigDecimal val = BigDecimal.ZERO;
                    if (total != null) {
                        val = new BigDecimal(total.toString());
                    }
                    return val.setScale(2, RoundingMode.HALF_UP);
                }));
    }

    public List<TransactionDTO> getTransactionsByDateRange(String userId, LocalDateTime start, LocalDateTime end) {
        Criteria criteria = Criteria.where("timestamp").gte(start).lte(end).and("userId").is(userId);
        return mongoTemplate.find(Query.query(criteria), Transaction.class)
                .stream()
                .sorted((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private Account debitAccount(String accountId, String userId, BigDecimal amount) {
        Query query = new Query(Criteria.where("id").is(accountId)
                .and("userId").is(userId)
                .and("balance").gte(amount));
        Update update = new Update().inc("balance", amount.negate());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Account.class);
    }

    private Account creditAccount(String accountId, String userId, BigDecimal amount) {
        Query query = new Query(Criteria.where("id").is(accountId)
                .and("userId").is(userId));
        Update update = new Update().inc("balance", amount);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Account.class);
    }

    private TransactionDTO convertToDTO(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(t.getId());
        dto.setFromAccountId(t.getFromAccountId());
        dto.setToAccountId(t.getToAccountId());
        dto.setAmount(t.getAmount());
        dto.setDescription(t.getDescription());
        dto.setReversalOfId(t.getReversalOfId());
        dto.setReversed(t.isReversed());
        dto.setTimestamp(t.getTimestamp());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}
