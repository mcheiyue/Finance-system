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

/**
 * 账户实体。
 *
 * <p><b>生产部署注意</b>：{@code idx_account_user_type_name} 为唯一复合索引。
 * 若线上 accounts 集合已存在 {@code (userId, type, name)} 重复数据，
 * 索引创建将失败。部署前需先排查并清理重复记录，例如：</p>
 * <pre>
 * db.accounts.aggregate([
 *   { $group: { _id: { userId: "$userId", type: "$type", name: "$name" }, cnt: { $sum: 1 } } },
 *   { $match: { cnt: { $gt: 1 } } }
 * ])
 * </pre>
 */
@Document(collection = "accounts")
@CompoundIndex(name = "idx_account_user_type_name", def = "{'userId': 1, 'type': 1, 'name': 1}", unique = true)
@Data
public class Account {
    @Id
    private String id;

    @Indexed
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
