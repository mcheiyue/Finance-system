package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.*;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.MonthlyReportRepository;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MongoTemplate mongoTemplate;

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
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSavingRate()));
        assertNotNull(result.getCategoryExpense());
        assertTrue(result.getCategoryExpense().isEmpty());
        assertNotNull(result.getCategoryIncome());
        assertTrue(result.getCategoryIncome().isEmpty());
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
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        incomeAccount.setName("工资");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setTimestamp(LocalDateTime.of(2026, 5, 15, 10, 0));

        monthlyReportService.onTransactionCreated(tx, incomeAccount, assetAccount);

        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void onTransactionCreated_updatesExpense() {
        String month = LocalDateTime.of(2026, 5, 20, 14, 30).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));

        Account expenseAccount = new Account();
        expenseAccount.setId("exp1");
        expenseAccount.setType(AccountType.EXPENSE);
        expenseAccount.setName("餐饮");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setAmount(new BigDecimal("350.00"));
        tx.setTimestamp(LocalDateTime.of(2026, 5, 20, 14, 30));

        monthlyReportService.onTransactionCreated(tx, assetAccount, expenseAccount);

        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void onTransactionReversed_decrementsIncome() {
        String month = LocalDateTime.of(2026, 5, 10, 9, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        incomeAccount.setName("工资");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("1000.00"));
        original.setTimestamp(LocalDateTime.of(2026, 5, 10, 9, 0));

        monthlyReportService.onTransactionReversed(original, incomeAccount, assetAccount);

        verify(monthlyReportRepository).findByUserIdAndMonth("user1", "2026-05");
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void onTransactionReversed_decrementsExpense() {
        String month = LocalDateTime.of(2026, 5, 12, 16, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));

        Account expenseAccount = new Account();
        expenseAccount.setId("exp1");
        expenseAccount.setType(AccountType.EXPENSE);
        expenseAccount.setName("餐饮");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("500.00"));
        original.setTimestamp(LocalDateTime.of(2026, 5, 12, 16, 0));

        monthlyReportService.onTransactionReversed(original, assetAccount, expenseAccount);

        verify(monthlyReportRepository).findByUserIdAndMonth("user1", "2026-05");
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void listReports_returnsOrderByMonthDesc() {
        when(transactionRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

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
    void listReports_backfillsMissingMonthFromHistoricalTransactions() {
        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        incomeAccount.setName("工资");
        incomeAccount.setBalance(new BigDecimal("3000.00"));

        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);
        assetAccount.setName("默认现金");
        assetAccount.setBalance(new BigDecimal("5000.00"));

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setFromAccountId("inc1");
        tx.setToAccountId("asset1");
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setTimestamp(LocalDateTime.of(2025, 12, 15, 10, 0));

        MonthlyReport report = new MonthlyReport();
        report.setId("report-2025-12");
        report.setUserId("user1");
        report.setMonth("2025-12");

        when(transactionRepository.findByUserId("user1")).thenReturn(List.of(tx));
        when(accountRepository.findByUserId("user1")).thenReturn(List.of(incomeAccount, assetAccount));
        when(monthlyReportRepository.existsByUserIdAndMonth("user1", "2025-12")).thenReturn(false);
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(invocation -> {
            MonthlyReport saved = invocation.getArgument(0);
            saved.setId("report-2025-12");
            return saved;
        });
        when(monthlyReportRepository.findByUserIdOrderByMonthDesc("user1")).thenReturn(List.of(report));

        List<MonthlyReport> result = monthlyReportService.listReports("user1");

        assertEquals(1, result.size());
        assertEquals("2025-12", result.get(0).getMonth());

        var reportCaptor = org.mockito.ArgumentCaptor.forClass(MonthlyReport.class);
        verify(monthlyReportRepository).save(reportCaptor.capture());
        MonthlyReport savedReport = reportCaptor.getValue();
        assertEquals(0, new BigDecimal("2000.00").compareTo(savedReport.getTotalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(savedReport.getTotalExpense()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(savedReport.getBalance()));
        assertEquals(1, savedReport.getTransactionCount());
        assertEquals(0, new BigDecimal("2000.00").compareTo(savedReport.getCategoryIncome().get("工资")));
        assertEquals(0, new BigDecimal("5000.00").compareTo(savedReport.getAccountBalances().get("asset1")));
        assertEquals(0, new BigDecimal("3000.00").compareTo(savedReport.getAccountBalances().get("inc1")));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void getReport_notFound_throwsException() {
        when(transactionRepository.findByUserId("user1")).thenReturn(Collections.emptyList());
        when(monthlyReportRepository.findByUserIdAndMonth("user1", "2026-05"))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> monthlyReportService.getReport("user1", "2026-05"));
    }

    @Test
    void onTransactionReversed_usesOriginalTransactionMonth() {
        String pastMonth = "2026-03";
        MonthlyReport report = new MonthlyReport();
        report.setId("report-march");
        report.setUserId("user1");
        report.setMonth(pastMonth);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", pastMonth))
                .thenReturn(Optional.of(report));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        incomeAccount.setName("工资");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("800.00"));
        original.setTimestamp(LocalDateTime.of(2026, 3, 15, 10, 0));

        monthlyReportService.onTransactionReversed(original, incomeAccount, assetAccount);

        verify(monthlyReportRepository).findByUserIdAndMonth("user1", pastMonth);
        verify(monthlyReportRepository, never()).findByUserIdAndMonth("user1", LocalDateTime.now().format(MONTH_FORMAT));
        verify(mongoTemplate, times(2)).updateFirst(
                argThat(q -> q.getQueryObject().getString("month").equals(pastMonth)),
                any(Update.class), eq(MonthlyReport.class));
    }

    @Test
    void onTransactionCreated_savingRateUpdateUsesAggregationPipeline() {
        String month = LocalDateTime.of(2026, 5, 15, 10, 0).format(MONTH_FORMAT);
        MonthlyReport report = new MonthlyReport();
        report.setId("report1");
        report.setUserId("user1");
        report.setMonth(month);
        when(monthlyReportRepository.findByUserIdAndMonth("user1", month))
                .thenReturn(Optional.of(report));

        Account incomeAccount = new Account();
        incomeAccount.setId("inc1");
        incomeAccount.setType(AccountType.INCOME);
        incomeAccount.setName("工资");
        Account assetAccount = new Account();
        assetAccount.setId("asset1");
        assetAccount.setType(AccountType.ASSET);

        Transaction tx = new Transaction();
        tx.setUserId("user1");
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setTimestamp(LocalDateTime.of(2026, 5, 15, 10, 0));

        monthlyReportService.onTransactionCreated(tx, incomeAccount, assetAccount);

        verify(monthlyReportRepository, times(1)).findByUserIdAndMonth("user1", month);
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MonthlyReport.class));
    }
}
