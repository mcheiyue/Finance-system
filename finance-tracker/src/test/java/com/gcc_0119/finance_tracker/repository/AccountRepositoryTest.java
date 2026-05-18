package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Necessity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void createAndQueryAccount_defaultBalanceIsZero() {
        Account account = new Account();
        account.setUserId("user123");
        account.setName("现金");
        account.setType(AccountType.ASSET);
        account.setNecessity(Necessity.NECESSARY);

        Account saved = accountRepository.save(account);

        assertNotNull(saved.getId());

        List<Account> accounts = accountRepository.findByUserId("user123");
        assertEquals(1, accounts.size());

        Account found = accounts.get(0);
        assertEquals("现金", found.getName());
        assertEquals(AccountType.ASSET, found.getType());
        assertEquals(0, BigDecimal.ZERO.compareTo(found.getBalance()));
        assertEquals(0, found.getVersion());
        assertFalse(found.isSystem());
        assertFalse(found.isFixed());
        assertEquals(Necessity.NECESSARY, found.getNecessity());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void findByUserIdAndType_returnsCorrectAccounts() {
        Account asset = new Account();
        asset.setUserId("user456");
        asset.setName("现金");
        asset.setType(AccountType.ASSET);

        Account expense = new Account();
        expense.setUserId("user456");
        expense.setName("餐饮");
        expense.setType(AccountType.EXPENSE);

        accountRepository.save(asset);
        accountRepository.save(expense);

        List<Account> assets = accountRepository.findByUserIdAndType("user456", AccountType.ASSET);
        assertEquals(1, assets.size());
        assertEquals("现金", assets.get(0).getName());

        List<Account> expenses = accountRepository.findByUserIdAndType("user456", AccountType.EXPENSE);
        assertEquals(1, expenses.size());
        assertEquals("餐饮", expenses.get(0).getName());
    }
}
