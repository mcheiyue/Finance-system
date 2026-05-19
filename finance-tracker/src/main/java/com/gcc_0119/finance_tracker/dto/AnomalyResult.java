package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AnomalyResult {
    private boolean anomalous;
    private List<String> warnings;
    private BigDecimal averageAmount;
    private BigDecimal currentAmount;
    
    public static AnomalyResult normal() {
        AnomalyResult result = new AnomalyResult();
        result.setAnomalous(false);
        return result;
    }
    
    public static AnomalyResult anomalous(List<String> warnings, BigDecimal averageAmount, BigDecimal currentAmount) {
        AnomalyResult result = new AnomalyResult();
        result.setAnomalous(true);
        result.setWarnings(warnings);
        result.setAverageAmount(averageAmount);
        result.setCurrentAmount(currentAmount);
        return result;
    }
}
