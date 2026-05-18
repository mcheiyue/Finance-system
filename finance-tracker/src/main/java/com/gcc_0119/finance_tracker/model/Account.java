package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "accounts")
@Data
public class Account {
    @Id
    private String id;
    private String userId;
    private String name;
    private AccountType type;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal balance = BigDecimal.ZERO;

    private int version = 0;
    private boolean isSystem;
    private boolean fixed;
    private Necessity necessity;
    private LocalDateTime createdAt = LocalDateTime.now();
}
