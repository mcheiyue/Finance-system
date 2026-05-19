package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.Budget;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.BudgetRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 创建或更新预算
     */
    public Budget setBudget(String userId, String accountId, BigDecimal limitAmount) {
        // 验证账户存在且属于用户
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(404, "账户不存在或无权访问"));

        // 验证是 EXPENSE 类型账户
        if (account.getType() != com.gcc_0119.finance_tracker.model.AccountType.EXPENSE) {
            throw new BusinessException("预算只能绑定到支出类账户");
        }

        // 验证金额
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("预算金额必须为正数");
        }

        // 查找或创建
        Budget budget = budgetRepository.findByUserIdAndAccountId(userId, accountId)
                .orElse(new Budget());
        
        budget.setUserId(userId);
        budget.setAccountId(accountId);
        budget.setLimitAmount(limitAmount);
        
        return budgetRepository.save(budget);
    }

    /**
     * 查询用户所有预算
     */
    public List<Budget> listBudgets(String userId) {
        return budgetRepository.findByUserId(userId);
    }

    /**
     * 查询预算执行情况（按月统计）
     * 
     * 关键：usedAmount 必须按月统计，不能用 EXPENSE.balance（balance 是历史累计）
     */
    public List<Map<String, Object>> getBudgetExecution(String userId) {
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        if (budgets.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取本月起止时间
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();

        // 查询本月所有支出交易（fromAccountId 在 EXPENSE 账户）
        List<String> expenseAccountIds = budgets.stream()
                .map(Budget::getAccountId)
                .collect(Collectors.toList());

        // 按月统计每个账户的支出
        Map<String, BigDecimal> monthlySpending = calculateMonthlySpending(userId, expenseAccountIds, monthStart, monthEnd);

        // 组装结果
        List<Map<String, Object>> results = new ArrayList<>();
        for (Budget budget : budgets) {
            Account account = accountRepository.findById(budget.getAccountId()).orElse(null);
            if (account == null) continue;

            BigDecimal usedAmount = monthlySpending.getOrDefault(budget.getAccountId(), BigDecimal.ZERO);
            BigDecimal remaining = budget.getLimitAmount().subtract(usedAmount);
            BigDecimal usageRate = budget.getLimitAmount().compareTo(BigDecimal.ZERO) > 0
                    ? usedAmount.divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> execution = new LinkedHashMap<>();
            execution.put("accountId", budget.getAccountId());
            execution.put("category", account.getName());
            execution.put("limitAmount", budget.getLimitAmount());
            execution.put("usedAmount", usedAmount);
            execution.put("remaining", remaining);
            execution.put("usageRate", usageRate);
            results.add(execution);
        }

        return results;
    }

    /**
     * 计算指定账户在指定时间范围内的支出总额
     * 
     * 通过查询 Transaction 集合，找出 fromAccountId 为指定账户的交易
     */
    private Map<String, BigDecimal> calculateMonthlySpending(String userId, List<String> accountIds,
            LocalDateTime start, LocalDateTime end) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)
                        .and("toAccountId").in(accountIds)
                        .and("timestamp").gte(start).lt(end)
                        .and("reversed").is(false)),
                Aggregation.group("toAccountId")
                        .sum("amount").as("total")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "transactions", Document.class);

        Map<String, BigDecimal> spending = new HashMap<>();
        for (Document doc : results.getMappedResults()) {
            String accountId = doc.getString("_id");
            BigDecimal total = doc.get("total", org.bson.types.Decimal128.class).bigDecimalValue();
            spending.put(accountId, total);
        }
        return spending;
    }

    /**
     * 删除预算
     */
    public void deleteBudget(String userId, String budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BusinessException(404, "预算不存在"));
        
        if (!budget.getUserId().equals(userId)) {
            throw new BusinessException(404, "预算不存在或无权访问");
        }
        
        budgetRepository.delete(budget);
    }
}
