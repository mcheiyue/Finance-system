package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AccountResponse;
import com.gcc_0119.finance_tracker.dto.CreateAccountRequest;
import com.gcc_0119.finance_tracker.dto.UpdateAccountRequest;
import com.gcc_0119.finance_tracker.exception.BusinessException;
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

    public List<AccountResponse> listAccounts(String userId, AccountType type) {
        List<Account> accounts;
        if (type != null) {
            accounts = accountRepository.findByUserIdAndType(userId, type);
        } else {
            accounts = accountRepository.findByUserId(userId);
        }
        return accounts.stream().map(AccountResponse::from).toList();
    }

    public AccountResponse getAccountById(String userId, String accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(404, "账户不存在"));
        return AccountResponse.from(account);
    }

    public AccountResponse createAccount(String userId, CreateAccountRequest request) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(request.getName());
        account.setType(request.getType());
        account.setBalance(BigDecimal.ZERO);
        account.setVersion(0);
        account.setSystem(false);
        account.setFixed(request.isFixed());
        account.setNecessity(request.getNecessity());
        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }

    public AccountResponse updateAccount(String userId, String accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(404, "账户不存在"));

        if (request.getName() != null) {
            account.setName(request.getName());
        }
        if (request.getFixed() != null) {
            account.setFixed(request.getFixed());
        }
        if (request.getNecessity() != null) {
            account.setNecessity(request.getNecessity());
        }

        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }

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
