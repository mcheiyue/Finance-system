package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Budget;
import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.BudgetRepository;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void setBudget_createsNewBudget() {
        Account expenseAccount = new Account();
        expenseAccount.setId("acc1");
        expenseAccount.setUserId("user1");
        expenseAccount.setName("餐饮");
        expenseAccount.setType(AccountType.EXPENSE);
        when(accountRepository.findByIdAndUserId("acc1", "user1")).thenReturn(Optional.of(expenseAccount));
        when(budgetRepository.findByUserIdAndAccountId("user1", "acc1")).thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setId("budget1");
            return b;
        });

        Budget result = budgetService.setBudget("user1", "acc1", new BigDecimal("500.00"));

        assertNotNull(result);
        assertEquals("user1", result.getUserId());
        assertEquals("acc1", result.getAccountId());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getLimitAmount()));
        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void setBudget_updatesExistingBudget() {
        Account expenseAccount = new Account();
        expenseAccount.setId("acc1");
        expenseAccount.setUserId("user1");
        expenseAccount.setName("餐饮");
        expenseAccount.setType(AccountType.EXPENSE);
        when(accountRepository.findByIdAndUserId("acc1", "user1")).thenReturn(Optional.of(expenseAccount));

        Budget existing = new Budget();
        existing.setId("budget1");
        existing.setUserId("user1");
        existing.setAccountId("acc1");
        existing.setLimitAmount(new BigDecimal("300.00"));
        when(budgetRepository.findByUserIdAndAccountId("user1", "acc1")).thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        Budget result = budgetService.setBudget("user1", "acc1", new BigDecimal("600.00"));

        assertEquals(0, new BigDecimal("600.00").compareTo(result.getLimitAmount()));
        assertEquals("budget1", result.getId());
        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void setBudget_rejectsNonExpenseAccount() {
        Account assetAccount = new Account();
        assetAccount.setId("acc1");
        assetAccount.setUserId("user1");
        assetAccount.setName("现金");
        assetAccount.setType(AccountType.ASSET);
        when(accountRepository.findByIdAndUserId("acc1", "user1")).thenReturn(Optional.of(assetAccount));

        assertThrows(BusinessException.class,
                () -> budgetService.setBudget("user1", "acc1", new BigDecimal("500.00")));
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void setBudget_rejectsInvalidAmount() {
        Account expenseAccount = new Account();
        expenseAccount.setId("acc1");
        expenseAccount.setUserId("user1");
        expenseAccount.setName("餐饮");
        expenseAccount.setType(AccountType.EXPENSE);
        when(accountRepository.findByIdAndUserId("acc1", "user1")).thenReturn(Optional.of(expenseAccount));

        assertThrows(BusinessException.class,
                () -> budgetService.setBudget("user1", "acc1", BigDecimal.ZERO));
        assertThrows(BusinessException.class,
                () -> budgetService.setBudget("user1", "acc1", new BigDecimal("-100")));
        assertThrows(BusinessException.class,
                () -> budgetService.setBudget("user1", "acc1", null));
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void listBudgets_returnsUserBudgets() {
        Budget b1 = new Budget();
        b1.setId("b1");
        b1.setUserId("user1");
        b1.setAccountId("acc1");
        b1.setLimitAmount(new BigDecimal("500"));
        Budget b2 = new Budget();
        b2.setId("b2");
        b2.setUserId("user1");
        b2.setAccountId("acc2");
        b2.setLimitAmount(new BigDecimal("300"));
        when(budgetRepository.findByUserId("user1")).thenReturn(List.of(b1, b2));

        List<Budget> result = budgetService.listBudgets("user1");

        assertEquals(2, result.size());
        assertEquals("b1", result.get(0).getId());
        assertEquals("b2", result.get(1).getId());
    }

    @Test
    void getBudgetExecution_calculatesMonthlySpending() {
        Budget budget = new Budget();
        budget.setId("b1");
        budget.setUserId("user1");
        budget.setAccountId("exp1");
        budget.setLimitAmount(new BigDecimal("1000.00"));
        when(budgetRepository.findByUserId("user1")).thenReturn(List.of(budget));

        Account expenseAccount = new Account();
        expenseAccount.setId("exp1");
        expenseAccount.setUserId("user1");
        expenseAccount.setName("餐饮");
        expenseAccount.setType(AccountType.EXPENSE);
        when(accountRepository.findById("exp1")).thenReturn(Optional.of(expenseAccount));

        Transaction t1 = new Transaction();
        t1.setFromAccountId("exp1");
        t1.setAmount(new BigDecimal("200.00"));
        Transaction t2 = new Transaction();
        t2.setFromAccountId("exp1");
        t2.setAmount(new BigDecimal("150.00"));
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(List.of(t1, t2));

        List<Map<String, Object>> results = budgetService.getBudgetExecution("user1");

        assertEquals(1, results.size());
        Map<String, Object> exec = results.get(0);
        assertEquals("exp1", exec.get("accountId"));
        assertEquals("餐饮", exec.get("category"));
        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) exec.get("limitAmount")));
        assertEquals(0, new BigDecimal("350.00").compareTo((BigDecimal) exec.get("usedAmount")));
        assertEquals(0, new BigDecimal("650.00").compareTo((BigDecimal) exec.get("remaining")));
        assertEquals(0, new BigDecimal("0.3500").compareTo((BigDecimal) exec.get("usageRate")));
    }

    @Test
    void getBudgetExecution_returnsEmptyForNoBudgets() {
        when(budgetRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

        List<Map<String, Object>> results = budgetService.getBudgetExecution("user1");

        assertTrue(results.isEmpty());
    }

    @Test
    void deleteBudget_removesBudget() {
        Budget budget = new Budget();
        budget.setId("b1");
        budget.setUserId("user1");
        budget.setAccountId("acc1");
        budget.setLimitAmount(new BigDecimal("500"));
        when(budgetRepository.findById("b1")).thenReturn(Optional.of(budget));

        budgetService.deleteBudget("user1", "b1");

        verify(budgetRepository).delete(budget);
    }

    @Test
    void deleteBudget_rejectsNonOwner() {
        Budget budget = new Budget();
        budget.setId("b1");
        budget.setUserId("otherUser");
        budget.setAccountId("acc1");
        budget.setLimitAmount(new BigDecimal("500"));
        when(budgetRepository.findById("b1")).thenReturn(Optional.of(budget));

        assertThrows(BusinessException.class,
                () -> budgetService.deleteBudget("user1", "b1"));
        verify(budgetRepository, never()).delete(any());
    }
}
