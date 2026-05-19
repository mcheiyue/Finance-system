package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.dto.BudgetResponse;
import com.gcc_0119.finance_tracker.dto.CreateBudgetRequest;
import com.gcc_0119.finance_tracker.dto.UpdateBudgetRequest;
import com.gcc_0119.finance_tracker.model.Budget;
import com.gcc_0119.finance_tracker.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    @Autowired
    private BudgetService budgetService;
    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<BudgetResponse>> listBudgets() {
        String userId = securityUtils.getCurrentUserId();
        List<Budget> budgets = budgetService.listBudgets(userId);
        List<BudgetResponse> responses = budgets.stream()
                .map(BudgetResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @GetMapping("/execution")
    public ApiResponse<List<Map<String, Object>>> getBudgetExecution() {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(budgetService.getBudgetExecution(userId));
    }

    @PostMapping
    public ApiResponse<BudgetResponse> createBudget(@RequestBody CreateBudgetRequest request) {
        String userId = securityUtils.getCurrentUserId();
        Budget budget = budgetService.setBudget(userId, request.getAccountId(), request.getLimitAmount());
        return ApiResponse.success(BudgetResponse.from(budget));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetResponse> updateBudget(@PathVariable String id, @RequestBody UpdateBudgetRequest request) {
        String userId = securityUtils.getCurrentUserId();
        // For simplicity, we'll use setBudget which handles create/update
        // The id parameter is not used directly since setBudget uses accountId
        // This is a design choice - budgets are identified by userId+accountId
        throw new UnsupportedOperationException("请使用 POST /api/budgets 更新预算（通过 accountId）");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBudget(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        budgetService.deleteBudget(userId, id);
        return ApiResponse.success(null);
    }
}
