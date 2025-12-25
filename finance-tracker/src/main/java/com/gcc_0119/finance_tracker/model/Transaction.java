package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal; 
import java.time.LocalDateTime;

@Document(collection = "transactions")
@Data
public class Transaction {
    @Id
    private String id;
    private String type;        
    private String category;    

    private BigDecimal amount;
    
    private String description; 
    private LocalDateTime timestamp;

    private String userId;
}