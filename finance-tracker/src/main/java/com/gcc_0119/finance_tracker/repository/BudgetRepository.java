package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends MongoRepository<Budget, String> {
    List<Budget> findByUserId(String userId);
    Optional<Budget> findByUserIdAndAccountId(String userId, String accountId);
    boolean existsByUserIdAndAccountId(String userId, String accountId);
}
