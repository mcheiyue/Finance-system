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
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
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
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class MonthlyReportService {

    @Autowired
    private MonthlyReportRepository monthlyReportRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

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
        report.setBalance(BigDecimal.ZERO);
        report.setSavingRate(BigDecimal.ZERO);
        report.setCategoryExpense(new HashMap<>());
        report.setCategoryIncome(new HashMap<>());
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
        update.inc("accountBalances." + fromAccount.getId(), amount.negate());
        update.inc("accountBalances." + toAccount.getId(), amount);

        if (fromAccount.getType() == AccountType.INCOME) {
            update.inc("totalIncome", amount);
            update.inc("balance", amount);
            update.inc("categoryIncome." + fromAccount.getName(), amount);
        } else if (toAccount.getType() == AccountType.EXPENSE) {
            update.inc("totalExpense", amount);
            update.inc("balance", amount.negate());
            update.inc("categoryExpense." + toAccount.getName(), amount);
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(transaction.getUserId()).and("month").is(month)),
                update, MonthlyReport.class);

        updateSavingRate(transaction.getUserId(), month);
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
        Update update = new Update().inc("transactionCount", 1).set("updatedAt", LocalDateTime.now());
        update.inc("accountBalances." + fromAccount.getId(), amount);
        update.inc("accountBalances." + toAccount.getId(), amount.negate());

        if (fromAccount.getType() == AccountType.INCOME) {
            update.inc("totalIncome", amount.negate());
            update.inc("balance", amount.negate());
            update.inc("categoryIncome." + fromAccount.getName(), amount.negate());
        } else if (toAccount.getType() == AccountType.EXPENSE) {
            update.inc("totalExpense", amount.negate());
            update.inc("balance", amount);
            update.inc("categoryExpense." + toAccount.getName(), amount.negate());
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(originalTransaction.getUserId()).and("month").is(month)),
                update, MonthlyReport.class);

        updateSavingRate(originalTransaction.getUserId(), month);
    }

    /**
     * 查询用户所有月报（自动回填历史缺失月报）
     */
    public List<MonthlyReport> listReports(String userId) {
        backfillMissingReports(userId);
        return monthlyReportRepository.findByUserIdOrderByMonthDesc(userId);
    }

    /**
     * 查询指定月报（自动回填历史缺失月报）
     */
    public MonthlyReport getReport(String userId, String month) {
        backfillMissingReports(userId);
        return monthlyReportRepository.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new BusinessException(404, "月报不存在"));
    }

    /**
     * 幂等回填：为历史交易生成缺失的月报文档。
     * 仅处理尚无月报文档的月份，已有月报的月份跳过。
     */
    private void backfillMissingReports(String userId) {
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
        if (allTransactions.isEmpty()) {
            return;
        }

        Map<String, List<Transaction>> byMonth = allTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTimestamp().format(MONTH_FORMAT)));

        Map<String, Account> accountMap = accountRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        for (Map.Entry<String, List<Transaction>> entry : byMonth.entrySet()) {
            String month = entry.getKey();
            if (monthlyReportRepository.existsByUserIdAndMonth(userId, month)) {
                continue;
            }

            List<Transaction> transactions = entry.getValue().stream()
                    .sorted(Comparator.comparing(Transaction::getTimestamp))
                    .collect(Collectors.toList());

            MonthlyReport report = buildBackfilledReport(userId, month, transactions, allTransactions, accountMap);
            try {
                monthlyReportRepository.save(report);
            } catch (DuplicateKeyException ignored) {
                // 并发下已有其他请求完成回填，忽略即可。
            }
        }
    }

    private MonthlyReport buildBackfilledReport(String userId,
                                                String month,
                                                List<Transaction> monthTransactions,
                                                List<Transaction> allTransactions,
                                                Map<String, Account> accountMap) {
        Map<String, BigDecimal> accountBalances = buildMonthEndBalances(month, allTransactions, accountMap);
        Map<String, BigDecimal> categoryExpense = new HashMap<>();
        Map<String, BigDecimal> categoryIncome = new HashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        int transactionCount = 0;

        for (Transaction tx : monthTransactions) {
            Account fromAccount = accountMap.get(tx.getFromAccountId());
            Account toAccount = accountMap.get(tx.getToAccountId());
            if (fromAccount == null || toAccount == null) {
                continue;
            }

            BigDecimal amount = tx.getAmount();
            transactionCount++;

            if (fromAccount.getType() == AccountType.INCOME) {
                totalIncome = totalIncome.add(amount);
                categoryIncome.merge(fromAccount.getName(), amount, BigDecimal::add);
            }

            if (toAccount.getType() == AccountType.EXPENSE) {
                totalExpense = totalExpense.add(amount);
                categoryExpense.merge(toAccount.getName(), amount, BigDecimal::add);
            }
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);
        BigDecimal savingRate = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingRate = balance
                    .divide(totalIncome, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        MonthlyReport report = new MonthlyReport();
        report.setUserId(userId);
        report.setMonth(month);
        report.setAccountBalances(accountBalances);
        report.setTotalIncome(totalIncome);
        report.setTotalExpense(totalExpense);
        report.setBalance(balance);
        report.setSavingRate(savingRate);
        report.setCategoryExpense(categoryExpense);
        report.setCategoryIncome(categoryIncome);
        report.setTransactionCount(transactionCount);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    private Map<String, BigDecimal> buildMonthEndBalances(String month,
                                                          List<Transaction> allTransactions,
                                                          Map<String, Account> accountMap) {
        Map<String, BigDecimal> balances = new HashMap<>();
        for (Account account : accountMap.values()) {
            balances.put(account.getId(), account.getBalance());
        }

        for (Transaction tx : allTransactions) {
            String txMonth = tx.getTimestamp().format(MONTH_FORMAT);
            if (txMonth.compareTo(month) <= 0) {
                continue;
            }

            BigDecimal amount = tx.getAmount();
            if (balances.containsKey(tx.getFromAccountId())) {
                balances.merge(tx.getFromAccountId(), amount, BigDecimal::add);
            }
            if (balances.containsKey(tx.getToAccountId())) {
                balances.merge(tx.getToAccountId(), amount.negate(), BigDecimal::add);
            }
        }

        return balances;
    }

    private void updateSavingRate(String userId, String month) {
        MonthlyReport report = monthlyReportRepository.findByUserIdAndMonth(userId, month).orElse(null);
        if (report == null) return;

        BigDecimal savingRate = BigDecimal.ZERO;
        if (report.getTotalIncome().compareTo(BigDecimal.ZERO) > 0) {
            savingRate = report.getBalance()
                    .divide(report.getTotalIncome(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(userId).and("month").is(month)),
                Update.update("savingRate", savingRate),
                MonthlyReport.class);
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
