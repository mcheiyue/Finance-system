package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    List<Transaction> findByType(String type);
    List<Transaction> findByCategory(String category);
    List<Transaction> findByTypeAndCategory(String type, String category);
    List<Transaction> findByUserId(String userId);
}