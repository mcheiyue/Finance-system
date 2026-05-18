# 三个死穴的解决方案

## 死穴一：CRUD 违反财务系统禁忌

### 问题本质

财务交易具有**不可变性（Immutability）**，物理修改/删除等于篡改审计记录。

### 解决方案：移除 Update/Delete，引入冲正交易

**1. 数据模型变更**

```java
public class Transaction {
    String id;
    String userId;
    BigDecimal amount;
    String fromAccountId;
    String toAccountId;
    String description;
    String reversalOfId;  // 冲正交易指向原交易ID
    boolean reversed;     // 原交易是否已被冲正
    LocalDateTime timestamp;
    LocalDateTime createdAt;
}
```

**2. 接口变更**

```
移除：
- PUT /api/transactions/:id
- DELETE /api/transactions/:id

新增：
- POST /api/transactions/:id/reverse  // 创建冲正交易
```

**3. 冲正逻辑**

```java
public Transaction reverse(String transactionId) {
    Transaction original = repository.findById(transactionId);
    
    if (original.isReversed()) {
        throw new BusinessException("该交易已被冲正");
    }
    
    // 创建冲正交易（from/to 反转）
    Transaction reversal = new Transaction();
    reversal.setFromAccountId(original.getToAccountId());
    reversal.setToAccountId(original.getFromAccountId());
    reversal.setAmount(original.getAmount());
    reversal.setDescription("冲正: " + original.getDescription());
    reversal.setReversalOfId(original.getId());
    reversal.setTimestamp(LocalDateTime.now());
    
    // 标记原交易已冲正
    original.setReversed(true);
    repository.save(original);
    
    return repository.save(reversal);
}
```

**4. 余额计算**

```java
// 计算余额时，跳过已冲正的交易
public BigDecimal calculateBalance(String accountId) {
    return repository.aggregate(
        match(
            or(
                and(eq("fromAccountId", accountId), eq("reversed", false)),
                and(eq("toAccountId", accountId), eq("reversed", false))
            )
        ),
        group(null,
            sum("inbound", cond(eq("toAccountId", accountId), "$amount", 0)),
            sum("outbound", cond(eq("fromAccountId", accountId), "$amount", 0))
        ),
        project(computed("balance", subtract("$inbound", "$outbound")))
    );
}
```

---

## 死穴二：并发超卖竞态条件

### 问题本质

"查询余额 → 扣款"不是原子操作，高并发下会出现 Race Condition。

### 解决方案：余额存储 + 原子更新

**1. 数据模型变更**

```java
public class Account {
    String id;
    String userId;
    String name;
    AccountType type;
    BigDecimal balance;  // 新增：实时余额
    int version;         // 新增：乐观锁版本号
    // ...
}
```

**2. 交易创建流程（原子操作）**

```java
@Transactional
public Transaction createTransaction(CreateTransactionRequest request) {
    String fromAccountId = request.getAccountId();
    String toAccountId = resolveToAccountId(request.getCategory(), request.getUserId());
    BigDecimal amount = request.getAmount();
    
    // Step 1: 原子扣款（使用 findAndModify）
    Query fromQuery = Query.query(Criteria.where("id").is(fromAccountId)
        .and("balance").gte(amount)  // 余额充足条件
        .and("version").is(getCurrentVersion(fromAccountId)));  // 乐观锁
    
    Update fromUpdate = new Update()
        .inc("balance", amount.negate())
        .inc("version", 1);
        
    Account fromAccount = mongoTemplate.findAndModify(
        fromQuery, 
        fromUpdate, 
        FindAndModifyOptions.options().returnNew(true), 
        Account.class
    );
    
    if (fromAccount == null) {
        throw new BusinessException("余额不足或账户已被修改");
    }
    
    // Step 2: 原子加款
    Query toQuery = Query.query(Criteria.where("id").is(toAccountId));
    Update toUpdate = new Update().inc("balance", amount);
    
    mongoTemplate.findAndModify(toQuery, toUpdate, Account.class);
    
    // Step 3: 创建交易记录
    Transaction transaction = new Transaction();
    transaction.setFromAccountId(fromAccountId);
    transaction.setToAccountId(toAccountId);
    transaction.setAmount(amount);
    transaction.setDescription(request.getDescription());
    transaction.setTimestamp(LocalDateTime.now());
    
    return repository.save(transaction);
}
```

**3. 并发安全验证**

```
假设账户余额 100，两笔 100 扣款同时到达：

时间线：
T1: 线程A 执行 findAndModify（balance >= 100）→ 成功，余额变为 0
T2: 线程B 执行 findAndModify（balance >= 100）→ 失败（余额为 0，不满足条件）
T3: 线程B 抛出异常 "余额不足"

结果：只有一笔交易成功，避免透支。
```

---

## 死穴三：全量聚合性能问题

### 问题本质

每次查余额/月报都要扫描全量交易，数据量大时性能崩溃。

### 解决方案：余额已在死穴二解决，月报引入快照

**1. 余额查询（已解决）**

Account.balance 字段存储实时余额，无需聚合查询。

**2. 月报快照**

```java
public class MonthlyReport {
    String id;
    String userId;
    String month;           // 格式：2026-05
    BigDecimal totalIncome;
    BigDecimal totalExpense;
    BigDecimal balance;     // 结余
    BigDecimal savingRate;  // 储蓄率
    Map<String, BigDecimal> categoryExpense;  // 分类支出
    Map<String, BigDecimal> categoryIncome;   // 分类收入
    int transactionCount;
    LocalDateTime updatedAt;
    
    // 索引：userId + month 唯一
}
```

**3. 月报更新策略**

```java
public void updateMonthlyReport(Transaction transaction) {
    String month = transaction.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    String userId = transaction.getUserId();
    
    Query query = Query.query(Criteria.where("userId").is(userId).and("month").is(month));
    
    Update update = new Update();
    update.inc("transactionCount", 1);
    
    // 判断是收入还是支出
    Account fromAccount = accountService.findById(transaction.getFromAccountId());
    Account toAccount = accountService.findById(transaction.getToAccountId());
    
    if (toAccount.getType() == AccountType.EXPENSE) {
        // 支出
        update.inc("totalExpense", transaction.getAmount());
        update.inc("categoryExpense." + toAccount.getName(), transaction.getAmount());
    } else if (fromAccount.getType() == AccountType.INCOME) {
        // 收入
        update.inc("totalIncome", transaction.getAmount());
        update.inc("categoryIncome." + fromAccount.getName(), transaction.getAmount());
    }
    
    // 更新结余和储蓄率
    // 注意：这需要使用 MongoDB 的 $expr 或应用层计算
    
    update.set("updatedAt", LocalDateTime.now());
    
    mongoTemplate.upsert(query, update, MonthlyReport.class);
}
```

**4. 月报查询**

```java
public MonthlyReport getMonthlyReport(String userId, String month) {
    Query query = Query.query(Criteria.where("userId").is(userId).and("month").is(month));
    MonthlyReport report = mongoTemplate.findOne(query, MonthlyReport.class);
    
    if (report == null) {
        // 首次查询，聚合生成
        report = generateMonthlyReport(userId, month);
        mongoTemplate.save(report);
    }
    
    return report;
}
```

---

## 总结

| 问题   | 解决方案                               | 核心机制                  |
| ------ | -------------------------------------- | ------------------------- |
| 死穴一 | 移除 Update/Delete，引入冲正交易       | 数据不可变性              |
| 死穴二 | 余额存储 + findAndModify 原子更新      | 单文档原子性 + 乐观锁     |
| 死穴三 | Account.balance + MonthlyReport 快照   | 预计算 + 增量更新         |

### 权衡与代价

**死穴二的代价**：
- 放弃了"余额实时聚合，不存储"的设计
- Account 文档需要维护 balance 字段
- 但获得了并发安全和查询性能

**死穴三的代价**：
- 月报数据不是实时精确，而是近实时（交易创建时增量更新）
- 冲正交易需要同步更新月报
- 但获得了查询性能

**死穴一的代价**：
- 移除了 Update/Delete 接口
- 用户纠错需要创建冲正交易（多一步操作）
- 但获得了数据完整性和审计追溯
