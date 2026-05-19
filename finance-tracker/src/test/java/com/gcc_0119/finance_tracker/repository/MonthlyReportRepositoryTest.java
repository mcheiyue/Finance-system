package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.MonthlyReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class MonthlyReportRepositoryTest {

    @Autowired
    private MonthlyReportRepository monthlyReportRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        IndexOperations indexOps = mongoTemplate.indexOps(MonthlyReport.class);
        indexOps.dropAllIndexes();
        indexOps.ensureIndex(
            new org.springframework.data.mongodb.core.index.CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("month", 1)
            ).unique().named("userId_month_unique")
        );
    }

    @AfterEach
    void tearDown() {
        monthlyReportRepository.deleteAll();
    }

    @Test
    void createAndQueryReport() {
        MonthlyReport report = new MonthlyReport();
        report.setUserId("user123");
        report.setMonth("2026-01");
        report.setTotalIncome(new BigDecimal("10000.00"));
        report.setTotalExpense(new BigDecimal("5000.00"));
        report.setTransactionCount(42);

        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("account1", new BigDecimal("5000.00"));
        balances.put("account2", new BigDecimal("3000.00"));
        report.setAccountBalances(balances);

        MonthlyReport saved = monthlyReportRepository.save(report);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("user123", saved.getUserId());
        assertEquals("2026-01", saved.getMonth());
        assertEquals(0, new BigDecimal("10000.00").compareTo(saved.getTotalIncome()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(saved.getTotalExpense()));
        assertEquals(42, saved.getTransactionCount());
        assertEquals(2, saved.getAccountBalances().size());
    }

    @Test
    void uniqueIndex_duplicateUserIdAndMonth_shouldFail() {
        MonthlyReport report1 = new MonthlyReport();
        report1.setUserId("user123");
        report1.setMonth("2026-01");
        report1.setTotalIncome(new BigDecimal("10000.00"));
        monthlyReportRepository.save(report1);

        MonthlyReport report2 = new MonthlyReport();
        report2.setUserId("user123");
        report2.setMonth("2026-01");
        report2.setTotalIncome(new BigDecimal("12000.00"));

        assertThrows(DuplicateKeyException.class, () -> monthlyReportRepository.save(report2));
    }

    @Test
    void findByUserIdOrderByMonthDesc_returnsList() {
        MonthlyReport report1 = new MonthlyReport();
        report1.setUserId("user123");
        report1.setMonth("2026-01");
        report1.setTotalIncome(new BigDecimal("10000.00"));

        MonthlyReport report2 = new MonthlyReport();
        report2.setUserId("user123");
        report2.setMonth("2026-03");
        report2.setTotalIncome(new BigDecimal("12000.00"));

        MonthlyReport report3 = new MonthlyReport();
        report3.setUserId("user123");
        report3.setMonth("2026-02");
        report3.setTotalIncome(new BigDecimal("11000.00"));

        MonthlyReport report4 = new MonthlyReport();
        report4.setUserId("user456");
        report4.setMonth("2026-01");
        report4.setTotalIncome(new BigDecimal("8000.00"));

        monthlyReportRepository.save(report1);
        monthlyReportRepository.save(report2);
        monthlyReportRepository.save(report3);
        monthlyReportRepository.save(report4);

        List<MonthlyReport> user123Reports = monthlyReportRepository.findByUserIdOrderByMonthDesc("user123");
        assertEquals(3, user123Reports.size());
        assertEquals("2026-03", user123Reports.get(0).getMonth());
        assertEquals("2026-02", user123Reports.get(1).getMonth());
        assertEquals("2026-01", user123Reports.get(2).getMonth());

        List<MonthlyReport> user456Reports = monthlyReportRepository.findByUserIdOrderByMonthDesc("user456");
        assertEquals(1, user456Reports.size());
    }

    @Test
    void findByUserIdAndMonth_returnsCorrectReport() {
        MonthlyReport report = new MonthlyReport();
        report.setUserId("user123");
        report.setMonth("2026-01");
        report.setTotalIncome(new BigDecimal("10000.00"));
        monthlyReportRepository.save(report);

        Optional<MonthlyReport> found = monthlyReportRepository.findByUserIdAndMonth("user123", "2026-01");
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("10000.00").compareTo(found.get().getTotalIncome()));

        Optional<MonthlyReport> notFound = monthlyReportRepository.findByUserIdAndMonth("user123", "2026-12");
        assertFalse(notFound.isPresent());
    }

    @Test
    void existsByUserIdAndMonth() {
        MonthlyReport report = new MonthlyReport();
        report.setUserId("user123");
        report.setMonth("2026-01");
        report.setTotalIncome(new BigDecimal("10000.00"));
        monthlyReportRepository.save(report);

        assertTrue(monthlyReportRepository.existsByUserIdAndMonth("user123", "2026-01"));
        assertFalse(monthlyReportRepository.existsByUserIdAndMonth("user123", "2026-12"));
        assertFalse(monthlyReportRepository.existsByUserIdAndMonth("nonexistent", "2026-01"));
    }
}
