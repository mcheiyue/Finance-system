package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AnomalyResult;
import com.gcc_0119.finance_tracker.model.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnomalyDetectionService {

    @Autowired
    private MongoTemplate mongoTemplate;

    static final BigDecimal ANOMALY_MULTIPLIER = new BigDecimal("3");
    static final int MIN_TRANSACTIONS_FOR_DETECTION = 5;
    static final int LOOKBACK_DAYS = 90;

    /**
     * 检测交易是否异常
     * 
     * @param userId 用户 ID
     * @param fromAccountId 来源账户 ID
     * @param amount 交易金额
     * @return 异常检测结果
     */
    public AnomalyResult detectAnomaly(String userId, String fromAccountId, BigDecimal amount) {
        // 查询该账户最近 90 天的交易
        LocalDateTime startDate = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("fromAccountId").is(fromAccountId)
                .and("timestamp").gte(startDate)
                .and("reversed").is(false);
        
        Query query = Query.query(criteria);
        List<Transaction> recentTransactions = mongoTemplate.find(query, Transaction.class);
        
        // 如果交易数量不足，不做检测
        if (recentTransactions.size() < MIN_TRANSACTIONS_FOR_DETECTION) {
            return AnomalyResult.normal();
        }
        
        // 计算平均金额
        BigDecimal totalAmount = recentTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageAmount = totalAmount.divide(
                BigDecimal.valueOf(recentTransactions.size()), 2, RoundingMode.HALF_UP);
        
        // 检测是否超过平均值的 3 倍
        BigDecimal threshold = averageAmount.multiply(ANOMALY_MULTIPLIER);
        
        if (amount.compareTo(threshold) > 0) {
            List<String> warnings = new ArrayList<>();
            warnings.add(String.format("交易金额 %.2f 超过该账户近 90 天平均金额 %.2f 的 3 倍", 
                    amount, averageAmount));
            
            return AnomalyResult.anomalous(warnings, averageAmount, amount);
        }
        
        return AnomalyResult.normal();
    }
}
