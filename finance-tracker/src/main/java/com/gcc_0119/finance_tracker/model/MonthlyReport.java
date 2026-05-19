package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
     * 结余 = totalIncome - totalExpense
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal balance = BigDecimal.ZERO;
    
    /**
     * 储蓄率 = balance / totalIncome（收入为0时为0）
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal savingRate = BigDecimal.ZERO;
    
    /**
     * 按分类统计支出
     * key: 分类账户名称, value: 该分类本月支出总额
     */
    private Map<String, BigDecimal> categoryExpense = new HashMap<>();
    
    /**
     * 按分类统计收入
     * key: 分类账户名称, value: 该分类本月收入总额
     */
    private Map<String, BigDecimal> categoryIncome = new HashMap<>();
    
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
