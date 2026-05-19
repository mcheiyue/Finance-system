package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateBudgetRequest {
    private BigDecimal limitAmount;
}
