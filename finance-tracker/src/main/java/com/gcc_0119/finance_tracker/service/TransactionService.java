package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.TransactionDTO;
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

    private TransactionDTO convertToDTO(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(t.getId());
        dto.setType(t.getType());
        dto.setCategory(t.getCategory());
        dto.setAmount(t.getAmount());
        dto.setDescription(t.getDescription());
        dto.setTimestamp(t.getTimestamp());
        return dto;
    }

    private Transaction convertToEntity(TransactionDTO dto) {
        Transaction t = new Transaction();

        t.setType(dto.getType());
        t.setCategory(dto.getCategory());
        t.setAmount(dto.getAmount());
        t.setDescription(dto.getDescription());
        t.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());
        return t;
    }

    public TransactionDTO save(TransactionDTO transactionDTO) {
        Transaction transaction = convertToEntity(transactionDTO);
        transaction.setUserId(getCurrentUserId());

        Transaction saved = transactionRepository.save(transaction);
        return convertToDTO(saved);
    }

    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findByUserId(getCurrentUserId()).stream()
                .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO update(String id, TransactionDTO transactionDTO) {
        Transaction existing = transactionRepository.findById(id).orElse(null);
        if (existing != null && existing.getUserId().equals(getCurrentUserId())) {
            existing.setAmount(transactionDTO.getAmount());
            existing.setType(transactionDTO.getType());
            existing.setCategory(transactionDTO.getCategory());
            existing.setDescription(transactionDTO.getDescription());
            existing.setTimestamp(transactionDTO.getTimestamp());

            Transaction saved = transactionRepository.save(existing);
            return convertToDTO(saved);
        } else {
            throw new RuntimeException("无权修改此记录或记录不存在");
        }
    }

    public List<TransactionDTO> getRecentTransactions(int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(days, java.time.temporal.ChronoUnit.DAYS);
        return getTransactionsByDateRange(start, now);
    }

    public List<TransactionDTO> getTransactionsByDateRange(LocalDateTime start, LocalDateTime end) {
        String userId = getCurrentUserId();
        Criteria criteria = Criteria.where("timestamp").gte(start).lte(end)
                .and("userId").is(userId);

        return mongoTemplate.find(
                org.springframework.data.mongodb.core.query.Query.query(criteria),
                Transaction.class).stream()
                .sorted((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                .map(this::convertToDTO)
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

    public Map<String, BigDecimal> getTotalByType(LocalDateTime start, LocalDateTime end) {

        String userId = getCurrentUserId();
        MatchOperation matchUser = Aggregation.match(
                Criteria.where("userId").is(userId)
                        .and("timestamp").gte(start).lte(end));
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

    public Map<String, BigDecimal> getTotalByCategory(String type, LocalDateTime start, LocalDateTime end) {

        String userId = getCurrentUserId();
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("timestamp").gte(start).lte(end);
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
}