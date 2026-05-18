# 实施计划（v2 - 修复三个死穴）

基于复式记账模型的设计决策，整合三个死穴的解决方案，按 Git 提交粒度组织。

## 核心设计变更

| 原设计                     | 新设计                             | 原因           |
| -------------------------- | ---------------------------------- | -------------- |
| Transaction 支持 CRUD      | 只支持 Create + Reversal           | 财务数据不可变 |
| 余额实时聚合               | Account.balance 存储 + 原子更新    | 并发安全       |
| 月报实时聚合               | MonthlyReport 快照 + 增量更新      | 查询性能       |

---

## 提交依赖关系

```
1.1 → 1.2 → 1.3
              ↓
2.1 → 2.2 → 2.3
              ↓
3.1 → 3.2 → 3.3
              ↓
4.1 → 4.2
              ↓
5.1, 5.2, 5.3, 5.4（可并行）
              ↓
6.1, 6.2, 6.3, 6.4, 6.5（可并行）
              ↓
7.1
```

---

## 阶段 1：后端核心模型

### Commit 1.1

**提交信息**：`feat(model): add Account entity with balance and version`

**变更内容**：
- 新增 `AccountType` 枚举：ASSET、LIABILITY、EQUITY、EXPENSE、INCOME
- 新增 `Necessity` 枚举：NECESSARY、OPTIONAL
- 新增 `Account` 实体：
  ```java
  public class Account {
      String id;
      String userId;
      String name;
      AccountType type;
      BigDecimal balance;      // 实时余额
      int version;             // 乐观锁版本号
      boolean isSystem;
      boolean fixed;
      Necessity necessity;
      LocalDateTime createdAt;
  }
  ```
- 新增 `AccountRepository`

**验收标准**：
- [ ] 编译通过
- [ ] 单元测试创建/查询账户
- [ ] balance 字段默认为 0

---

### Commit 1.2

**提交信息**：`feat(service): add AccountService with preset account initialization`

**变更内容**：
- 新增 `AccountService`：CRUD + 注册时预置虚拟账户逻辑
- 预置账户清单：
  - ASSET：默认现金（balance=0）
  - EQUITY：Opening-Balance（balance=0）
  - EXPENSE：餐饮、交通、购物、娱乐、住房、医疗、教育、其他（balance=0）
  - INCOME：工资、兼职、投资、红包、其他收入（balance=0）
- 修改 `UserService.register()`，注册成功后调用预置逻辑

**验收标准**：
- [ ] 新用户注册后，accounts 集合自动生成预置账户
- [ ] 预置账户 isSystem = true
- [ ] 所有账户 balance = 0

---

### Commit 1.3

**提交信息**：`feat(api): add AccountController with balance query`

**变更内容**：
- 新增 `AccountController`：
  - GET /api/accounts（支持按 type 筛选）
  - GET /api/accounts/:id
  - POST /api/accounts（支持懒创建）
  - PUT /api/accounts/:id（仅修改 name/fixed/necessity，不修改 balance）
- balance 字段只读，不允许直接修改

**验收标准**：
- [ ] Postman 测试所有接口
- [ ] PUT 无法修改 balance 字段

---

## 阶段 2：Transaction 改造

### Commit 2.1

**提交信息**：`refactor(model): migrate Transaction to double-entry with immutability`

**变更内容**：
- Transaction 实体变更：
  ```java
  public class Transaction {
      String id;
      String userId;
      BigDecimal amount;
      String fromAccountId;
      String toAccountId;
      String description;
      String reversalOfId;    // 冲正交易指向原交易ID
      boolean reversed;       // 原交易是否已被冲正
      LocalDateTime timestamp;
      LocalDateTime createdAt;
  }
  ```
- 新增索引：fromAccountId、toAccountId、reversalOfId

**验收标准**：
- [ ] 编译通过
- [ ] 索引创建成功

---

### Commit 2.2

**提交信息**：`refactor(service): rewrite TransactionService with atomic updates`

**变更内容**：
- **移除**：updateTransaction、deleteTransaction 方法
- **新增**：createTransaction（原子更新余额）
  ```java
  public Transaction createTransaction(CreateTransactionRequest request) {
      // 1. 原子扣款
      Account fromAccount = mongoTemplate.findAndModify(
          Query.query(Criteria.where("id").is(fromAccountId)
              .and("balance").gte(amount)
              .and("version").is(currentVersion)),
          Update.update("balance", amount.negate()).inc("version", 1),
          FindAndModifyOptions.options().returnNew(true),
          Account.class
      );
      
      if (fromAccount == null) {
          throw new BusinessException("余额不足或账户已被修改");
      }
      
      // 2. 原子加款
      mongoTemplate.findAndModify(
          Query.query(Criteria.where("id").is(toAccountId)),
          Update.update("balance", amount),
          Account.class
      );
      
      // 3. 创建交易记录
      Transaction transaction = new Transaction();
      // ... 设置字段
      return repository.save(transaction);
  }
  ```
- **新增**：reverseTransaction（冲正）
  ```java
  public Transaction reverseTransaction(String transactionId) {
      Transaction original = repository.findById(transactionId);
      
      if (original.isReversed()) {
          throw new BusinessException("该交易已被冲正");
      }
      
      // 创建反向交易
      Transaction reversal = new Transaction();
      reversal.setFromAccountId(original.getToAccountId());
      reversal.setToAccountId(original.getFromAccountId());
      reversal.setAmount(original.getAmount());
      reversal.setDescription("冲正: " + original.getDescription());
      reversal.setReversalOfId(original.getId());
      reversal.setTimestamp(LocalDateTime.now());
      
      // 原子更新余额（反向）
      // ... 类似 createTransaction 的原子更新逻辑
      
      // 标记原交易已冲正
      original.setReversed(true);
      repository.save(original);
      
      return repository.save(reversal);
  }
  ```

**验收标准**：
- [ ] 无 updateTransaction、deleteTransaction 方法
- [ ] 余额不足时创建失败
- [ ] 并发创建不会透支
- [ ] 冲正交易正确反转余额
- [ ] 冲正接口不可重复成功调用（必须防二次冲正，避免生成两笔 reversal）

---

### Commit 2.3

**提交信息**：`refactor(api): update TransactionController for double-entry`

**变更内容**：
- 更新接口：
  ```
  POST /api/transactions          // 创建交易
  POST /api/transactions/:id/reverse  // 冲正交易
  GET  /api/transactions          // 查询交易（分页在 Commit 5.1 补齐）
  GET  /api/transactions/:id      // 查询单个交易
  ```
- 移除接口：
  ```
  PUT    /api/transactions/:id    // 已移除
  DELETE /api/transactions/:id    // 已移除
  ```

**验收标准**：
- [ ] Postman 测试创建/冲正/查询
- [ ] PUT/DELETE 返回 405 Method Not Allowed

---

## 阶段 3：预算系统

### Commit 3.1

**提交信息**：`feat(model): add Budget entity`

**变更内容**：
- 新增 `Budget` 实体：
  ```java
  public class Budget {
      String id;
      String userId;
      String accountId;
      BigDecimal limitAmount;
      LocalDateTime createdAt;
  }
  ```
- 新增 `BudgetRepository`
- 新增唯一索引：userId + accountId

**验收标准**：
- [ ] 编译通过
- [ ] 唯一索引生效

---

### Commit 3.2

**提交信息**：`feat(service): add BudgetService with monthly execution calculation`

**变更内容**：
- 新增 `BudgetService`：
  - CRUD 操作
  - 查询本月预算执行情况
  - **执行情况 usedAmount 必须按月统计**（预算规则常驻，但执行口径按月）
    - 禁止用 `EXPENSE` 账户 `balance` 计算 usedAmount（balance 代表历史累计，会导致第二个月起预算必然爆仓）
    - usedAmount 推荐来源：`MonthlyReport.categoryExpense`（MonthlyReport 未落地前，可先按月范围聚合 Transaction 作为过渡实现）
- 返回结构：
  ```json
  {
    "accountId": "xxx",
    "category": "餐饮",
    "limitAmount": 2000,
    "usedAmount": 1500,
    "remaining": 500,
    "usageRate": 0.75
  }
  ```

**验收标准**：
- [ ] 设置预算后，查询返回正确数据
- [ ] 使用比例计算正确

---

### Commit 3.3

**提交信息**：`feat(api): add BudgetController`

**变更内容**：
- 新增 `BudgetController`：
  - GET /api/budgets
  - GET /api/budgets/execution
  - POST /api/budgets
  - PUT /api/budgets/:id
  - DELETE /api/budgets/:id

**验收标准**：
- [ ] Postman 测试所有接口

---

## 阶段 4：月报中心

### Commit 4.1

**提交信息**：`feat(model): add MonthlyReport snapshot entity`

**变更内容**：
- 新增 `MonthlyReport` 实体：
  ```java
  public class MonthlyReport {
      String id;
      String userId;
      String month;           // 格式：2026-05
      BigDecimal totalIncome;
      BigDecimal totalExpense;
      BigDecimal balance;     // 结余
      BigDecimal savingRate;  // 储蓄率
      Map<String, BigDecimal> categoryExpense;
      Map<String, BigDecimal> categoryIncome;
      int transactionCount;
      LocalDateTime updatedAt;
  }
  ```
- 新增 `MonthlyReportRepository`
- 新增唯一索引：userId + month

**验收标准**：
- [ ] 编译通过
- [ ] 唯一索引生效

---

### Commit 4.2

**提交信息**：`feat(service): add MonthlyReportService with incremental updates`

**变更内容**：
- 新增 `MonthlyReportService`：
  - `updateMonthlyReport(Transaction transaction)`：增量更新
  - `getMonthlyReport(String userId, String month)`：查询快照
- 交易创建时自动调用 `updateMonthlyReport`
- 冲正交易时同步更新月报

**验收标准**：
- [ ] 创建交易后，月报数据自动更新
- [ ] 冲正交易后，月报数据回滚
- [ ] 查询月报返回快照（无需聚合）

---

## 阶段 5：辅助功能

### Commit 5.1

**提交信息**：`feat(api): add pagination and filtering to TransactionController`

**变更内容**：
- GET /api/transactions 参数：
  - page（默认 0）
  - size（默认 20）
  - sort（默认 timestamp,desc）
  - keyword（搜索 description）
  - startDate、endDate
  - accountId
- 使用 MongoTemplate 手动分页

**验收标准**：
- [ ] 分页参数生效
- [ ] 筛选条件正确

---

### Commit 5.2

**提交信息**：`feat(api): add CSV import with preview`

**变更内容**：
- POST /api/transactions/import/preview：解析 CSV，返回预览 + 标记重复
- POST /api/transactions/import/confirm：确认后批量写入（使用原子更新）

**验收标准**：
- [ ] 上传 CSV，预览正确
- [ ] 确认后数据入库，余额正确更新

---

### Commit 5.3

**提交信息**：`refactor(auth): session recovery via /api/users/me (no new verify endpoint)`

**变更内容**：
- 不新增 `/api/auth/verify`
- 会话恢复统一复用 `GET /api/users/me`

**验收标准**：
- [ ] 有效 token 调用 `/api/users/me` 返回 200 + 用户信息
- [ ] 无效/过期 token 调用 `/api/users/me` 返回 401（前端 401 拦截器清理 token 并跳转登录）

---

### Commit 5.4

**提交信息**：`feat(service): add anomaly detection`

**变更内容**：
- 在 `TransactionService.createTransaction` 中添加异常检测
- 规则：单笔金额超过该分类历史均值的 2 倍
- 返回交易 + 警告信息

**验收标准**：
- [ ] 异常交易时响应包含警告
- [ ] 正常交易无警告

---

## 阶段 6：前端改造

### Commit 6.1

**提交信息**：`feat(ui): load categories dynamically`

**变更内容**：
- 分类选择器从 GET /api/accounts?type=EXPENSE 动态加载
- 支持"+ 新增分类"按钮
- 交易创建入参改为 `fromAccountId/toAccountId`（后端接口层不再接受 type/category）

**验收标准**：
- [ ] 分类列表正确
- [ ] 新增分类后立即可选

---

### Commit 6.2

**提交信息**：`feat(ui): migrate budget page to backend API`

**变更内容**：
- 预算设置页调用 POST /api/budgets
- 预算展示页调用 GET /api/budgets/execution
- 移除 localStorage 中的 finance_budgets

**验收标准**：
- [ ] 预算功能正常

---

### Commit 6.3

**提交信息**：`feat(ui): add monthly report page`

**变更内容**：
- 新增月报页面，调用 GET /api/reports/monthly

**验收标准**：
- [ ] 月报数据正确

---

### Commit 6.4

**提交信息**：`feat(ui): add CSV import page`

**变更内容**：
- 新增导入页面，支持上传 CSV、预览、确认

**验收标准**：
- [ ] 导入流程完整

---

### Commit 6.5

**提交信息**：`feat(auth): implement session recovery`

**变更内容**：
- AuthContext 启动时调用 `GET /api/users/me`
- axios 拦截器统一处理 401

**验收标准**：
- [ ] token 过期时自动跳转登录页

---

## 阶段 7：数据迁移

### Commit 7.1

**提交信息**：`feat(migration): add script to convert legacy data`

**变更内容**：
- 为每个用户创建默认 ASSET 账户（balance = 初始余额总和）
- 将旧 Transaction 转换为新格式
- 计算并设置各账户的 balance

**验收标准**：
- [ ] 迁移后数据可正常查询
- [ ] 余额计算正确

---

## 进度跟踪

| 阶段 | 提交 | 状态 | 修复死穴 |
| ---- | ---- | ---- | -------- |
| 1    | 1.1  | ⬜   | 死穴二   |
| 1    | 1.2  | ⬜   |          |
| 1    | 1.3  | ⬜   |          |
| 2    | 2.1  | ⬜   | 死穴一   |
| 2    | 2.2  | ⬜   | 死穴一+二|
| 2    | 2.3  | ⬜   | 死穴一   |
| 3    | 3.1  | ⬜   |          |
| 3    | 3.2  | ⬜   |          |
| 3    | 3.3  | ⬜   |          |
| 4    | 4.1  | ⬜   | 死穴三   |
| 4    | 4.2  | ⬜   | 死穴三   |
| 5    | 5.1  | ⬜   |          |
| 5    | 5.2  | ⬜   |          |
| 5    | 5.3  | ⬜   |          |
| 5    | 5.4  | ⬜   |          |
| 6    | 6.1  | ⬜   |          |
| 6    | 6.2  | ⬜   |          |
| 6    | 6.3  | ⬜   |          |
| 6    | 6.4  | ⬜   |          |
| 6    | 6.5  | ⬜   |          |
| 7    | 7.1  | ⬜   |          |
