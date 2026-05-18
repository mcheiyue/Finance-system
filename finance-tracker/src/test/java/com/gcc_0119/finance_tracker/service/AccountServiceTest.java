package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AccountResponse;
import com.gcc_0119.finance_tracker.dto.CreateAccountRequest;
import com.gcc_0119.finance_tracker.dto.UpdateAccountRequest;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Necessity;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void initializePresetAccounts_createsAll15Accounts() {
        when(accountRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

        accountService.initializePresetAccounts("user1");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(15)).save(captor.capture());

        List<Account> saved = captor.getAllValues();
        for (Account a : saved) {
            assertEquals("user1", a.getUserId());
            assertTrue(a.isSystem());
            assertEquals(0, BigDecimal.ZERO.compareTo(a.getBalance()));
            assertNotNull(a.getName());
            assertNotNull(a.getType());
        }
    }

    @Test
    void initializePresetAccounts_skipsWhenAlreadyInitialized() {
        Account existingSystem = new Account();
        existingSystem.setSystem(true);
        when(accountRepository.findByUserId("user1")).thenReturn(List.of(existingSystem));

        accountService.initializePresetAccounts("user1");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void initializePresetAccounts_createsCorrectAccountNames() {
        when(accountRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

        accountService.initializePresetAccounts("user1");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(15)).save(captor.capture());

        List<String> names = captor.getAllValues().stream()
                .map(Account::getName)
                .toList();

        assertTrue(names.contains("默认现金"));
        assertTrue(names.contains("Opening-Balance"));
        assertTrue(names.contains("餐饮"));
        assertTrue(names.contains("交通"));
        assertTrue(names.contains("购物"));
        assertTrue(names.contains("娱乐"));
        assertTrue(names.contains("住房"));
        assertTrue(names.contains("医疗"));
        assertTrue(names.contains("教育"));
        assertTrue(names.contains("其他"));
        assertTrue(names.contains("工资"));
        assertTrue(names.contains("兼职"));
        assertTrue(names.contains("投资"));
        assertTrue(names.contains("红包"));
        assertTrue(names.contains("其他收入"));
    }

    @Test
    void initializePresetAccounts_createsCorrectAccountTypes() {
        when(accountRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

        accountService.initializePresetAccounts("user1");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(15)).save(captor.capture());

        List<Account> saved = captor.getAllValues();

        long assetCount = saved.stream()
                .filter(a -> a.getType() == AccountType.ASSET).count();
        long equityCount = saved.stream()
                .filter(a -> a.getType() == AccountType.EQUITY).count();
        long expenseCount = saved.stream()
                .filter(a -> a.getType() == AccountType.EXPENSE).count();
        long incomeCount = saved.stream()
                .filter(a -> a.getType() == AccountType.INCOME).count();

        assertEquals(1, assetCount);
        assertEquals(1, equityCount);
        assertEquals(8, expenseCount);
        assertEquals(5, incomeCount);
    }

    @Test
    void initializePresetAccounts_withNonSystemAccountsStillInitializes() {
        Account userCreated = new Account();
        userCreated.setSystem(false);
        when(accountRepository.findByUserId("user1")).thenReturn(List.of(userCreated));

        accountService.initializePresetAccounts("user1");

        verify(accountRepository, times(15)).save(any());
    }

    @Test
    void listAccounts_returnsAllAccountsForUser() {
        Account a1 = new Account();
        a1.setId("a1");
        a1.setUserId("user1");
        a1.setName("现金");
        a1.setType(AccountType.ASSET);
        a1.setBalance(BigDecimal.TEN);
        Account a2 = new Account();
        a2.setId("a2");
        a2.setUserId("user1");
        a2.setName("餐饮");
        a2.setType(AccountType.EXPENSE);
        a2.setBalance(BigDecimal.ZERO);
        when(accountRepository.findByUserId("user1")).thenReturn(List.of(a1, a2));

        List<AccountResponse> result = accountService.listAccounts("user1", null);

        assertEquals(2, result.size());
        assertEquals("a1", result.get(0).getId());
        assertEquals("a2", result.get(1).getId());
    }

    @Test
    void listAccounts_withTypeFilter_returnsFilteredAccounts() {
        Account expense = new Account();
        expense.setId("e1");
        expense.setUserId("user1");
        expense.setName("餐饮");
        expense.setType(AccountType.EXPENSE);
        expense.setBalance(BigDecimal.ZERO);
        when(accountRepository.findByUserIdAndType("user1", AccountType.EXPENSE))
                .thenReturn(List.of(expense));

        List<AccountResponse> result = accountService.listAccounts("user1", AccountType.EXPENSE);

        assertEquals(1, result.size());
        assertEquals(AccountType.EXPENSE, result.get(0).getType());
    }

    @Test
    void getAccountById_returnsAccountWhenOwnerMatches() {
        Account account = new Account();
        account.setId("a1");
        account.setUserId("user1");
        account.setName("现金");
        account.setType(AccountType.ASSET);
        account.setBalance(BigDecimal.TEN);
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.of(account));

        AccountResponse result = accountService.getAccountById("user1", "a1");

        assertEquals("a1", result.getId());
        assertEquals("现金", result.getName());
    }

    @Test
    void getAccountById_throwsWhenNotFound() {
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> accountService.getAccountById("user1", "a1"));
    }

    @Test
    void createAccount_setsDefaultsCorrectly() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName("自定义账户");
        request.setType(AccountType.EXPENSE);
        request.setFixed(false);
        request.setNecessity(Necessity.OPTIONAL);

        Account savedAccount = new Account();
        savedAccount.setId("newId");
        savedAccount.setUserId("user1");
        savedAccount.setName("自定义账户");
        savedAccount.setType(AccountType.EXPENSE);
        savedAccount.setBalance(BigDecimal.ZERO);
        savedAccount.setVersion(0);
        savedAccount.setSystem(false);
        savedAccount.setFixed(false);
        savedAccount.setNecessity(Necessity.OPTIONAL);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse result = accountService.createAccount("user1", request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account captured = captor.getValue();

        assertEquals("user1", captured.getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(captured.getBalance()));
        assertEquals(0, captured.getVersion());
        assertFalse(captured.isSystem());
        assertEquals("自定义账户", captured.getName());
        assertEquals(AccountType.EXPENSE, captured.getType());
        assertEquals("newId", result.getId());
    }

    @Test
    void updateAccount_onlyUpdatesWhitelistedFields() {
        Account existing = new Account();
        existing.setId("a1");
        existing.setUserId("user1");
        existing.setName("旧名称");
        existing.setType(AccountType.EXPENSE);
        existing.setBalance(new BigDecimal("100.00"));
        existing.setVersion(5);
        existing.setSystem(true);
        existing.setFixed(false);
        existing.setNecessity(Necessity.OPTIONAL);
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("新名称");
        request.setFixed(true);
        request.setNecessity(Necessity.NECESSARY);

        AccountResponse result = accountService.updateAccount("user1", "a1", request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account captured = captor.getValue();

        assertEquals("新名称", captured.getName());
        assertTrue(captured.isFixed());
        assertEquals(Necessity.NECESSARY, captured.getNecessity());

        assertEquals(AccountType.EXPENSE, captured.getType());
        assertEquals("user1", captured.getUserId());
        assertTrue(captured.isSystem());
        assertEquals(5, captured.getVersion());
        assertEquals(0, new BigDecimal("100.00").compareTo(captured.getBalance()));

        assertEquals("新名称", result.getName());
    }

    @Test
    void updateAccount_doesNotModifyBalance() {
        Account existing = new Account();
        existing.setId("a1");
        existing.setUserId("user1");
        existing.setName("旧名称");
        existing.setType(AccountType.ASSET);
        existing.setBalance(new BigDecimal("500.00"));
        existing.setVersion(0);
        existing.setSystem(false);
        existing.setFixed(false);
        existing.setNecessity(Necessity.NECESSARY);
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("新名称");

        accountService.updateAccount("user1", "a1", request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account captured = captor.getValue();

        assertEquals(0, new BigDecimal("500.00").compareTo(captured.getBalance()));
    }

    @Test
    void updateAccount_throwsWhenNotFound() {
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.empty());

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("新名称");

        assertThrows(BusinessException.class,
                () -> accountService.updateAccount("user1", "a1", request));
    }

    @Test
    void updateAccount_partialUpdateOnlyName() {
        Account existing = new Account();
        existing.setId("a1");
        existing.setUserId("user1");
        existing.setName("旧名称");
        existing.setType(AccountType.INCOME);
        existing.setBalance(BigDecimal.ZERO);
        existing.setVersion(0);
        existing.setSystem(false);
        existing.setFixed(true);
        existing.setNecessity(Necessity.NECESSARY);
        when(accountRepository.findByIdAndUserId("a1", "user1")).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("新名称");

        accountService.updateAccount("user1", "a1", request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account captured = captor.getValue();

        assertEquals("新名称", captured.getName());
        assertTrue(captured.isFixed());
        assertEquals(Necessity.NECESSARY, captured.getNecessity());
    }
}
