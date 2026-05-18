package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionDTO {
    private String id;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String description;
    private String reversalOfId;
    private boolean reversed;
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
}