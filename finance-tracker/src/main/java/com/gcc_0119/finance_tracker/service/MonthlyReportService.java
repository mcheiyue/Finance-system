package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.event.TransactionCreatedEvent;
import com.gcc_0119.finance_tracker.event.TransactionReversedEvent;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.MonthlyReport;
import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.MonthlyReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MonthlyReportService {

    @Autowired
    private MonthlyReportRepository monthlyReportRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 获取或创建本月月报
     */
    public MonthlyReport getOrCreateCurrentMonthReport(String userId) {
        String currentMonth = LocalDateTime.now().format(MONTH_FORMAT);
        return getOrCreateReport(userId, currentMonth);
    }

    /**
     * 获取或创建指定月份月报
     */
    public MonthlyReport getOrCreateReport(String userId, String month) {
        return monthlyReportRepository.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> {
                    try {
                        return createNewReport(userId, month);
                    } catch (DuplicateKeyException e) {
                        return monthlyReportRepository.findByUserIdAndMonth(userId, month)
                                .orElseThrow(() -> e);
                    }
                });
    }

    /**
     * 创建新月报（快照当前账户余额）
     */
    private MonthlyReport createNewReport(String userId, String month) {
        List<Account> accounts = accountRepository.findByUserId(userId);

        Map<String, BigDecimal> balances = new HashMap<>();
        for (Account account : accounts) {
            balances.put(account.getId(), account.getBalance());
        }

        MonthlyReport report = new MonthlyReport();
        report.setUserId(userId);
        report.setMonth(month);
        report.setAccountBalances(balances);
        report.setTotalIncome(BigDecimal.ZERO);
        report.setTotalExpense(BigDecimal.ZERO);
        report.setTransactionCount(0);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        return monthlyReportRepository.save(report);
    }

    /**
     * 交易创建时增量更新月报
     *
     * @param transaction 新创建的交易
     * @param fromAccount 来源账户
     * @param toAccount 目标账户
     */
    public void onTransactionCreated(Transaction transaction, Account fromAccount, Account toAccount) {
        String month = transaction.getTimestamp().format(MONTH_FORMAT);
        getOrCreateReport(transaction.getUserId(), month);

        BigDecimal amount = transaction.getAmount();
        Update update = new Update().inc("transactionCount", 1).set("updatedAt", LocalDateTime.now());

        if (fromAccount.getType() == AccountType.INCOME) {
            update.inc("totalIncome", amount);
        } else if (toAccount.getType() == AccountType.EXPENSE) {
            update.inc("totalExpense", amount);
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(transaction.getUserId()).and("month").is(month)),
                update, MonthlyReport.class);
    }

    /**
     * 交易冲正时增量更新月报
     *
     * @param originalTransaction 被冲正的原始交易
     * @param fromAccount 来源账户
     * @param toAccount 目标账户
     */
    public void onTransactionReversed(Transaction originalTransaction, Account fromAccount, Account toAccount) {
        String month = LocalDateTime.now().format(MONTH_FORMAT);
        getOrCreateReport(originalTransaction.getUserId(), month);

        BigDecimal amount = originalTransaction.getAmount();
        Update update = new Update().set("updatedAt", LocalDateTime.now());

        if (fromAccount.getType() == AccountType.INCOME) {
            update.inc("totalIncome", amount.negate());
        } else if (toAccount.getType() == AccountType.EXPENSE) {
            update.inc("totalExpense", amount.negate());
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(originalTransaction.getUserId()).and("month").is(month)),
                update, MonthlyReport.class);
    }

    /**
     * 查询用户所有月报
     */
    public List<MonthlyReport> listReports(String userId) {
        return monthlyReportRepository.findByUserIdOrderByMonthDesc(userId);
    }

    /**
     * 查询指定月报
     */
    public MonthlyReport getReport(String userId, String month) {
        return monthlyReportRepository.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new BusinessException(404, "月报不存在"));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        onTransactionCreated(event.getTransaction(), event.getFromAccount(), event.getToAccount());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionReversed(TransactionReversedEvent event) {
        onTransactionReversed(event.getOriginalTransaction(), event.getFromAccount(), event.getToAccount());
    }
}
