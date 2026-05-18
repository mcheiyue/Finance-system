package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
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
}
