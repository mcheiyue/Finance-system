package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "transactions")
@CompoundIndex(name = "idx_tx_user_timestamp", def = "{'userId': 1, 'timestamp': -1}")
@Data
public class Transaction {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    @Indexed
    private String fromAccountId;

    @Indexed
    private String toAccountId;

    private String description;

    @Indexed
    private String reversalOfId;

    private boolean reversed = false;

    @Indexed
    private LocalDateTime timestamp;

    private LocalDateTime createdAt = LocalDateTime.now();
}
