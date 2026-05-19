package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateBudgetRequest {
    private String accountId;
    private BigDecimal limitAmount;
}
