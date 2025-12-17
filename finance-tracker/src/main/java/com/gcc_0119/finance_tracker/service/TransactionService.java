package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.model.User;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import com.gcc_0119.finance_tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.bson.Document;

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
    private MongoTemplate mongoTemplate;
    @Autowired
    private UserRepository userRepository;

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found"));
        return user.getId();
    }

    public Transaction save(Transaction transaction) {
        transaction.setUserId(getCurrentUserId());

        if (transaction.getTimestamp() == null) {
            transaction.setTimestamp(LocalDateTime.now());
        }
        return transactionRepository.save(transaction);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findByUserId(getCurrentUserId()).stream()
                .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        Transaction t = transactionRepository.findById(id).orElse(null);
        if (t != null && t.getUserId().equals(getCurrentUserId())) {
            transactionRepository.deleteById(id);
        } else {
            throw new RuntimeException("无权删除此记录或记录不存在");
        }
    }

    public Map<String, BigDecimal> getTotalByType() {
        String userId = getCurrentUserId();
        MatchOperation matchUser = Aggregation.match(Criteria.where("userId").is(userId));

        GroupOperation group = Aggregation.group("type").sum("amount").as("total");
        Aggregation agg = Aggregation.newAggregation(matchUser, group);

        List<Document> results = mongoTemplate.aggregate(agg, "transactions", Document.class).getMappedResults();

        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r.get("_id"),
                        r -> {
                            Object total = r.get("total");
                            if (total == null)
                                return BigDecimal.ZERO;
                            return new BigDecimal(total.toString()).setScale(2, RoundingMode.HALF_UP);
                        }));
    }

    public Map<String, BigDecimal> getTotalByCategory(String type) {
        String userId = getCurrentUserId();
        Criteria criteria = Criteria.where("userId").is(userId);
        if (type != null && !type.isEmpty()) {
            criteria.and("type").is(type);
        }

        MatchOperation match = Aggregation.match(criteria);
        GroupOperation group = Aggregation.group("category").sum("amount").as("total");

        Aggregation agg = Aggregation.newAggregation(match, group);

        List<Document> results = mongoTemplate.aggregate(agg, "transactions", Document.class).getMappedResults();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r.get("_id"),
                        r -> {
                            Object total = r.get("total");
                            if (total == null)
                                return BigDecimal.ZERO;
                            return new BigDecimal(total.toString()).setScale(2, RoundingMode.HALF_UP);
                        }));
    }

    public List<Transaction> getTransactionsByDateRange(LocalDateTime start, LocalDateTime end) {
        String userId = getCurrentUserId();
        Criteria criteria = Criteria.where("timestamp").gte(start).lte(end)
                .and("userId").is(userId);

        return mongoTemplate.find(
                org.springframework.data.mongodb.core.query.Query.query(criteria),
                Transaction.class).stream()
                .sorted((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                .collect(Collectors.toList());
    }

    public Transaction update(String id, Transaction transaction) {
        Transaction existing = transactionRepository.findById(id).orElse(null);
        if (existing != null && existing.getUserId().equals(getCurrentUserId())) {
            existing.setAmount(transaction.getAmount());
            existing.setType(transaction.getType());
            existing.setCategory(transaction.getCategory());
            existing.setDescription(transaction.getDescription());
            existing.setTimestamp(transaction.getTimestamp());
            return transactionRepository.save(existing);
        } else {
            throw new RuntimeException("无权修改此记录或记录不存在");
        }
    }
}