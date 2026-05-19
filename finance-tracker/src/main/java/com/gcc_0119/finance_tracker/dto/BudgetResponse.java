package com.gcc_0119.finance_tracker.dto;

import com.gcc_0119.finance_tracker.model.Budget;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BudgetResponse {
    private String id;
    private String userId;
    private String accountId;
    private BigDecimal limitAmount;
    private LocalDateTime createdAt;

    public static BudgetResponse from(Budget budget) {
        BudgetResponse response = new BudgetResponse();
        response.setId(budget.getId());
        response.setUserId(budget.getUserId());
        response.setAccountId(budget.getAccountId());
        response.setLimitAmount(budget.getLimitAmount());
        response.setCreatedAt(budget.getCreatedAt());
        return response;
    }
}
