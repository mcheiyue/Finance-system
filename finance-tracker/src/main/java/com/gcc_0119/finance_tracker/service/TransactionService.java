package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AnomalyResult;
import com.gcc_0119.finance_tracker.dto.PaginatedResponse;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.event.TransactionCreatedEvent;
import com.gcc_0119.finance_tracker.event.TransactionReversedEvent;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private AnomalyDetectionService anomalyDetectionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
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

        Account fromAcc = accountRepository.findByIdAndUserId(fromAccountId, userId)
                .orElseThrow(() -> new BusinessException(404, "转出账户不存在或无权访问"));
        Account toAcc = accountRepository.findByIdAndUserId(toAccountId, userId)
                .orElseThrow(() -> new BusinessException(404, "转入账户不存在或无权访问"));

        AnomalyResult anomalyResult = anomalyDetectionService.detectAnomaly(userId, fromAccountId, amount);

        debitAccount(fromAcc, userId, amount);

        creditAccount(toAcc, userId, amount);

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setFromAccountId(fromAccountId);
        transaction.setToAccountId(toAccountId);
        transaction.setAmount(amount);
        transaction.setDescription(request.getDescription());
        transaction.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now());

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionCreatedEvent(saved, fromAcc, toAcc));

        TransactionDTO dto = convertToDTO(saved);
        if (anomalyResult.isAnomalous()) {
            dto.setAnomalyWarnings(anomalyResult.getWarnings());
        }
        return dto;
    }

    @Transactional
    public TransactionDTO reverseTransaction(String userId, String transactionId) {
        Query markReversedQuery = new Query(Criteria.where("id").is(transactionId)
                .and("userId").is(userId)
                .and("reversed").is(false));
        Update markReversedUpdate = new Update().set("reversed", true);
        Transaction original = mongoTemplate.findAndModify(markReversedQuery, markReversedUpdate,
                FindAndModifyOptions.options().returnNew(true), Transaction.class);
        if (original == null) {
            Transaction existing = transactionRepository.findById(transactionId).orElse(null);
            if (existing == null) {
                throw new BusinessException(404, "交易记录不存在");
            }
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(404, "交易记录不存在或无权访问");
            }
            throw new BusinessException("该交易已被冲正，不能重复冲正");
        }

        BigDecimal amount = original.getAmount();

        Account fromAcc = accountRepository.findByIdAndUserId(original.getFromAccountId(), userId)
                .orElseThrow(() -> new BusinessException(500, "冲正失败：原转出账户不存在"));
        creditAccount(fromAcc, userId, amount);

        Account toAcc = accountRepository.findByIdAndUserId(original.getToAccountId(), userId)
                .orElseThrow(() -> new BusinessException(500, "冲正失败：转入账户不存在"));
        debitAccount(toAcc, userId, amount);

        Transaction reversal = new Transaction();
        reversal.setUserId(userId);
        reversal.setFromAccountId(original.getToAccountId());
        reversal.setToAccountId(original.getFromAccountId());
        reversal.setAmount(amount);
        reversal.setDescription("冲正: " + (original.getDescription() != null ? original.getDescription() : ""));
        reversal.setReversalOfId(transactionId);
        reversal.setTimestamp(LocalDateTime.now());

        Transaction savedReversal = transactionRepository.save(reversal);
        eventPublisher.publishEvent(new TransactionReversedEvent(original, fromAcc, toAcc));
        return convertToDTO(savedReversal);
    }

    public List<TransactionDTO> getAllTransactions(String userId) {
        return transactionRepository.findByUserId(userId).stream()
                .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO getTransactionById(String userId, String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        if (!transaction.getUserId().equals(userId)) {
            throw new BusinessException(404, "交易记录不存在或无权访问");
        }
        return convertToDTO(transaction);
    }

    public List<TransactionDTO> getTransactionsByDateRange(String userId, LocalDateTime start, LocalDateTime end) {
        Criteria criteria = Criteria.where("timestamp").gte(start).lte(end).and("userId").is(userId);
        return mongoTemplate.find(Query.query(criteria), Transaction.class)
                .stream()
                .sorted((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询交易
     */
    public PaginatedResponse<TransactionDTO> getTransactionsPaginated(String userId, int page, int size,
                                                                       String fromAccountId, String toAccountId,
                                                                       LocalDateTime startDate, LocalDateTime endDate,
                                                                       String keyword) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));

        if (fromAccountId != null) {
            query.addCriteria(Criteria.where("fromAccountId").is(fromAccountId));
        }
        if (toAccountId != null) {
            query.addCriteria(Criteria.where("toAccountId").is(toAccountId));
        }
        if (startDate != null) {
            query.addCriteria(Criteria.where("timestamp").gte(startDate));
        }
        if (endDate != null) {
            query.addCriteria(Criteria.where("timestamp").lte(endDate));
        }
        if (keyword != null && !keyword.isBlank()) {
            query.addCriteria(Criteria.where("description").regex(Pattern.quote(keyword), "i"));
        }

        long total = mongoTemplate.count(query, Transaction.class);

        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        query.skip((long) page * size).limit(size);

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PaginatedResponse.of(dtos, page, size, total);
    }

    private Account debitAccount(Account account, String userId, BigDecimal amount) {
        Query query = new Query(Criteria.where("id").is(account.getId())
                .and("userId").is(userId)
                .and("version").is(account.getVersion()));
        if (account.getType() == AccountType.ASSET) {
            query.addCriteria(Criteria.where("balance").gte(amount));
        }
        Update update = new Update().inc("balance", amount.negate()).inc("version", 1);
        Account result = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Account.class);
        if (result == null) {
            Account currentAcc = mongoTemplate.findById(account.getId(), Account.class);
            if (currentAcc != null && currentAcc.getType() == AccountType.ASSET
                    && currentAcc.getBalance().compareTo(amount) < 0) {
                throw new BusinessException("余额不足");
            }
            throw new BusinessException(509, "系统繁忙，请重试");
        }
        return result;
    }

    private Account creditAccount(Account account, String userId, BigDecimal amount) {
        Query query = new Query(Criteria.where("id").is(account.getId())
                .and("userId").is(userId)
                .and("version").is(account.getVersion()));
        Update update = new Update().inc("balance", amount).inc("version", 1);
        Account result = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Account.class);
        if (result == null) {
            throw new BusinessException(509, "系统繁忙，请重试");
        }
        return result;
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
