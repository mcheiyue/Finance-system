package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends MongoRepository<Account, String> {
    List<Account> findByUserId(String userId);
    List<Account> findByUserIdAndType(String userId, AccountType type);
    Optional<Account> findByIdAndUserId(String id, String userId);
    Optional<Account> findByUserIdAndName(String userId, String name);
}
