package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.MonthlyReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends MongoRepository<MonthlyReport, String> {
    List<MonthlyReport> findByUserIdOrderByMonthDesc(String userId);
    Optional<MonthlyReport> findByUserIdAndMonth(String userId, String month);
    boolean existsByUserIdAndMonth(String userId, String month);
}
