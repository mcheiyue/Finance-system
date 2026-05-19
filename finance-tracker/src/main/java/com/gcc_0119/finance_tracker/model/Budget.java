package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "budgets")
@CompoundIndex(def = "{'userId': 1, 'accountId': 1}", unique = true)
@Data
public class Budget {
    @Id
    private String id;
    private String userId;
    private String accountId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal limitAmount;
    private LocalDateTime createdAt = LocalDateTime.now();
}
