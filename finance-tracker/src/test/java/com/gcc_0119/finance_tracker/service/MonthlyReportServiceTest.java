package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.*;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.MonthlyReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    @Mock
    private MonthlyReportRepository monthlyReportRepository;
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private MonthlyReportService monthlyReportService;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Test
    void getOrCreateCurrentMonthReport_createsNew() {
        String currentMonth = LocalDateTime.now().format(MONTH_FORMAT);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", currentMonth))
                .thenReturn(Optional.empty());

        Account acc1 = new Account();
        acc1.setId("acc1");
        acc1.setBalance(new BigDecimal("1000.00"));
        Account acc2 = new Account();
        acc2.setId("acc2");
        acc2.setBalance(new BigDecimal("500.00"));
        when(accountRepository.findByUserId("user1")).thenReturn(List.of(acc1, acc2));

        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> {
            MonthlyReport r = inv.getArgument(0);
            r.setId("report1");
            return r;
        });

        MonthlyReport result = monthlyReportService.getOrCreateCurrentMonthReport("user1");

        assertNotNull(result);
        assertEquals("user1", result.getUserId());
        assertEquals(currentMonth, result.getMonth());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalExpense()));
        assertEquals(0, result.getTransactionCount());
        assertEquals(2, result.getAccountBalances().size());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.getAccountBalances().get("acc1")));
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getAccountBalances().get("acc2")));
        verify(monthlyReportRepository).save(any(MonthlyReport.class));
    }

    @Test
    void getOrCreateCurrentMonthReport_returnsExisting() {
        String currentMonth = LocalDateTime.now().format(MONTH_FORMAT);
        MonthlyReport existing = new MonthlyReport();
        existing.setId("report1");
        existing.setUserId("user1");
        existing.setMonth(currentMonth);
        existing.setTotalIncome(new BigDecimal("3000.00"));
        existing.setTotalExpense(new BigDecimal("1500.00"));
        existing.setTransactionCount(10);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", currentMonth))
                .thenReturn(Optional.of(existing));

        MonthlyReport result = monthlyReportService.getOrCreateCurrentMonthReport("user1");

        assertEquals("report1", result.getId());
        assertEquals(0, new BigDecimal("3000.00").compareTo(result.getTotalIncome()));
        verify(monthlyReportRepository, never()).save(any());
    }

    @Test
    void onTransactionCreated_updatesIncome() {
        String month = LocalDateTime.of(2026, 5, 15, 10, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        report.setTotalIncome(BigDecimal.ZERO);
        report.setTotalExpense(BigDecimal.ZERO);
        report.setTransactionCount(0);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setTimestamp(LocalDateTime.of(2026, 5, 15, 10, 0));

        monthlyReportService.onTransactionCreated(tx, assetAccount, incomeAccount);

        assertEquals(0, new BigDecimal("2000.00").compareTo(report.getTotalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalExpense()));
        assertEquals(1, report.getTransactionCount());
        verify(monthlyReportRepository).save(report);
    }

    @Test
    void onTransactionCreated_updatesExpense() {
        String month = LocalDateTime.of(2026, 5, 20, 14, 30).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        report.setTotalIncome(new BigDecimal("5000.00"));
        report.setTotalExpense(new BigDecimal("100.00"));
        report.setTransactionCount(3);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        Account expenseAccount = new Account();
        expenseAccount.setId("exp1");
        expenseAccount.setType(AccountType.EXPENSE);
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setAmount(new BigDecimal("350.00"));
        tx.setTimestamp(LocalDateTime.of(2026, 5, 20, 14, 30));

        monthlyReportService.onTransactionCreated(tx, expenseAccount, assetAccount);

        assertEquals(0, new BigDecimal("5000.00").compareTo(report.getTotalIncome()));
        assertEquals(0, new BigDecimal("450.00").compareTo(report.getTotalExpense()));
        assertEquals(4, report.getTransactionCount());
        verify(monthlyReportRepository).save(report);
    }

    @Test
    void onTransactionReversed_decrementsIncome() {
        String month = LocalDateTime.of(2026, 5, 10, 9, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        report.setTotalIncome(new BigDecimal("3000.00"));
        report.setTotalExpense(BigDecimal.ZERO);
        report.setTransactionCount(5);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("1000.00"));
        original.setTimestamp(LocalDateTime.of(2026, 5, 10, 9, 0));

        monthlyReportService.onTransactionReversed(original, assetAccount, incomeAccount);

        assertEquals(0, new BigDecimal("2000.00").compareTo(report.getTotalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalExpense()));
        assertEquals(5, report.getTransactionCount());
        verify(monthlyReportRepository).save(report);
    }

    @Test
    void onTransactionReversed_decrementsExpense() {
        String month = LocalDateTime.of(2026, 5, 12, 16, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        report.setTotalIncome(BigDecimal.ZERO);
        report.setTotalExpense(new BigDecimal("2000.00"));
        report.setTransactionCount(8);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        Account expenseAccount = new Account();
        expenseAccount.setId("exp1");
        expenseAccount.setType(AccountType.EXPENSE);
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("500.00"));
        original.setTimestamp(LocalDateTime.of(2026, 5, 12, 16, 0));

        monthlyReportService.onTransactionReversed(original, expenseAccount, assetAccount);

        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalIncome()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(report.getTotalExpense()));
        assertEquals(8, report.getTransactionCount());
        verify(monthlyReportRepository).save(report);
    }

    @Test
    void listReports_returnsOrderByMonthDesc() {
        MonthlyReport r1 = new MonthlyReport();
        r1.setId("r1");
        r1.setMonth("2026-05");
        MonthlyReport r2 = new MonthlyReport();
        r2.setId("r2");
        r2.setMonth("2026-04");
        when(monthlyReportRepository.findByUserIdOrderByMonthDesc("user1"))
                .thenReturn(List.of(r1, r2));

        List<MonthlyReport> result = monthlyReportService.listReports("user1");

        assertEquals(2, result.size());
        assertEquals("r1", result.get(0).getId());
        assertEquals("r2", result.get(1).getId());
    }

    @Test
    void getReport_notFound_throwsException() {
        when(monthlyReportRepository.findByUserIdAndMonth("user1", "2026-05"))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> monthlyReportService.getReport("user1", "2026-05"));
    }
}
