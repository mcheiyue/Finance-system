package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Necessity;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    /**
     * 为新注册用户初始化预设系统账户。
     * 如果该用户已有系统账户（即已初始化过），则跳过以避免重复创建。
     *
     * @param userId 用户ID
     */
    public void initializePresetAccounts(String userId) {
        List<Account> existing = accountRepository.findByUserId(userId);
        boolean alreadyInitialized = existing.stream().anyMatch(Account::isSystem);
        if (alreadyInitialized) {
            return;
        }

        createAccount(userId, "默认现金", AccountType.ASSET, Necessity.NECESSARY);

        createAccount(userId, "Opening-Balance", AccountType.EQUITY, Necessity.NECESSARY);

        createAccount(userId, "餐饮", AccountType.EXPENSE, Necessity.NECESSARY);
        createAccount(userId, "交通", AccountType.EXPENSE, Necessity.NECESSARY);
        createAccount(userId, "购物", AccountType.EXPENSE, Necessity.OPTIONAL);
        createAccount(userId, "娱乐", AccountType.EXPENSE, Necessity.OPTIONAL);
        createAccount(userId, "住房", AccountType.EXPENSE, Necessity.NECESSARY);
        createAccount(userId, "医疗", AccountType.EXPENSE, Necessity.NECESSARY);
        createAccount(userId, "教育", AccountType.EXPENSE, Necessity.NECESSARY);
        createAccount(userId, "其他", AccountType.EXPENSE, Necessity.OPTIONAL);

        createAccount(userId, "工资", AccountType.INCOME, Necessity.NECESSARY);
        createAccount(userId, "兼职", AccountType.INCOME, Necessity.OPTIONAL);
        createAccount(userId, "投资", AccountType.INCOME, Necessity.OPTIONAL);
        createAccount(userId, "红包", AccountType.INCOME, Necessity.OPTIONAL);
        createAccount(userId, "其他收入", AccountType.INCOME, Necessity.OPTIONAL);
    }

    private void createAccount(String userId, String name, AccountType type, Necessity necessity) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(name);
        account.setType(type);
        account.setBalance(BigDecimal.ZERO);
        account.setSystem(true);
        account.setNecessity(necessity);
        accountRepository.save(account);
    }
}
