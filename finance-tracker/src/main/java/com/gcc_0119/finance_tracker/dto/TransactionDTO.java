package com.gcc_0119.finance_tracker.dto;

import lombok.Data; 
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data 
public class TransactionDTO {
    private String id;
    private String type;
    private String category;
    private BigDecimal amount;
    private String description;
    private LocalDateTime timestamp;
}