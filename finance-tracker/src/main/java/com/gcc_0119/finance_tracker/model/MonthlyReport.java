package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "monthly_reports")
@Data
public class MonthlyReport {
    @Id
    private String id;
    
    private String userId;
    
    /**
     * 月份标识，格式：2026-01
     */
    private String month;
    
    /**
     * 各账户余额快照
     * key: accountId, value: balance
     */
    private Map<String, BigDecimal> accountBalances;
    
    /**
     * 本月总收入
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalIncome = BigDecimal.ZERO;
    
    /**
     * 本月总支出
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalExpense = BigDecimal.ZERO;
    
    /**
     * 本月交易笔数
     */
    private int transactionCount = 0;
    
    /**
     * 快照创建时间
     */
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt = LocalDateTime.now();
}
