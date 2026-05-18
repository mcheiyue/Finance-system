# 详细实施方案（供审核稿）

> 说明：本文件是在 `implementation-plan.md（v2）` 的基础上，将每个 Commit 细化为**文件级修改清单 + 关键代码骨架 + 验证步骤 + 注意事项**，方便你逐条审核。
>
> 约束：
> - **不改动** `D:\Class\FW_AppDev\refactor\implementation-plan.md`（你要求另起一份）。
> - 仅描述“将要如何改”，不在此阶段直接改工程代码。
>
## 0. 项目实际结构（与探索结果对齐）

- 后端目录：`D:\Github\Finance-system\finance-tracker`
  - Maven + Java 21 + Spring Boot 3.5.8
  - 包根：`com.gcc_0119.finance_tracker`
  - 现有关键类（已确认存在）：
    - `model/User.java`、`model/Transaction.java`
    - `dto/TransactionDTO.java`（当前仍是 type/category 版 DTO）
    - `service/TransactionService.java`（含 save/update/delete + 聚合统计）
    - `controller/TransactionController.java`（POST/GET/PUT/DELETE + stats）
    - `exception/BusinessException.java` + `exception/GlobalExceptionHandler.java`
    - `config/WebSecurityConfig.java`（/api/auth/** 放行，其余需鉴权）
    - `common/ApiResponse.java`、`common/SecurityUtils.java`
- 前端目录：`D:\Github\Finance-system\finance-tracker-frontend`
  - React 19 + Vite 7 + Ant Design 6，JS/JSX（无 TS）
  - 现有关键文件（已确认存在）：
    - `src/api/transaction.js`（含 update/delete）
    - `src/pages/HomePage.jsx`（编辑/删除交易 + 本地预算 localStorage）
    - `src/pages/StatisticsPage.jsx`（按 type/category 统计）
    - `src/AuthContext.jsx`（会话恢复当前仅检查 token 存在）
    - `src/utils/request.js`（axios 拦截器，401 会清 token 跳登录）

## 1. 总体策略（审核点）

### 1.1 为什么要拆模型（从 type/category → 复式记账）

- 现状：交易只有 `type(income/expense) + category(字符串)`，无法表达“账户间转移/负债/权益”等复式口径。
- 目标：引入 `Account`（账户/分类本质）+ `Transaction(fromAccountId,toAccountId)`。

### 1.2 三个死穴的落地方式（必须在文档中被显式贯彻）

1) **不可变性**：移除 Update/Delete，改为“冲正交易（Reversal）”。
2) **并发安全**：余额不再聚合实时算，改为 `Account.balance + findAndModify 原子更新 + version`。
3) **报表性能**：引入 `MonthlyReport` 快照 + 增量更新。

### 1.3 兼容/迁移策略（前后端如何不断档）

- 后端：先引入新模型与新接口，再逐步把旧接口替换为 405/移除；统计接口在重构后保持路径不变或提供兼容层。
- 前端：先把“创建交易表单”改为选择账户（按类型分组展示 Account），强制提交 `fromAccountId/toAccountId`，再删除 update/delete 的 UI 能力，加入冲正按钮。

---

## 阶段 1：后端核心模型（Account）

### Commit 1.1 — `feat(model): add Account entity with balance and version`

#### 目标
- 新增账户实体 `Account` 与枚举 `AccountType/Necessity`，为并发安全与复式记账打基础。

#### 文件级改动
新增：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/model/AccountType.java`
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/model/Necessity.java`
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/model/Account.java`
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/repository/AccountRepository.java`

（可选）新增测试：
- `finance-tracker/src/test/java/com/gcc_0119/finance_tracker/repository/AccountRepositoryTest.java`

#### 关键实现要点（审核点）
- `Account.balance`：`@Field(targetType = FieldType.DECIMAL128)` 与 Transaction.amount 的 DECIMAL128 风格一致。
- `Account.version`：int，默认 0，用于 `findAndModify` 条件更新。
- 账户唯一性：建议加 `userId + type + name` 唯一复合索引，避免重复分类账户。（若你认为允许同名分类，则改为非唯一索引）

#### 关键代码骨架（供审核）

`model/AccountType.java`
```java
package com.gcc_0119.finance_tracker.model;

public enum AccountType {
    ASSET, LIABILITY, EQUITY, EXPENSE, INCOME
}
```

`model/Necessity.java`
```java
package com.gcc_0119.finance_tracker.model;

public enum Necessity {
    NECESSARY,
    OPTIONAL
}
```

`model/Account.java`（关键字段）
```java
@Document(collection = "accounts")
@Data
@CompoundIndex(name = "idx_account_user_type_name", def = "{'userId': 1, 'type': 1, 'name': 1}", unique = true)
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
    private boolean isSystem = false;
    private boolean fixed = false;
    private Necessity necessity;
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

#### 验证
- 后端编译：`mvn -q -DskipTests package`
- 若能跑测试：`mvn -q test`

---

### Commit 1.2 — `feat(service): add AccountService with preset account initialization`

#### 目标
- 用户注册后自动初始化预置账户（系统账户），并保证幂等。

#### 文件级改动
新增：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/service/AccountService.java`

修改：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/service/UserService.java`
  - 在 `registerUser(...)` 成功保存用户后，调用 `accountService.initPresetAccountsForUser(saved.getId())`

#### 关键实现要点（审核点）
- 预置账户清单：
  - ASSET：现金
  - EQUITY：Opening-Balance
  - EXPENSE：餐饮/交通/购物/娱乐/住房/医疗/教育/其他（可带 necessity/fixed 标签）
  - INCOME：工资/兼职/投资/红包/其他
- `ensureSystemAccount(...)`：使用 `findByUserIdAndTypeAndName` 做幂等。

#### 验证
- 注册新用户后：MongoDB `accounts` 集合出现该用户的预置账户；`isSystem=true`，`balance=0`。

---

### Commit 1.3 — `feat(api): add AccountController with balance query`

#### 目标
- 提供账户查询/创建接口；**禁止**客户端直接改 balance。

#### 文件级改动
新增：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/controller/AccountController.java`
（如采用 DTO）新增：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/dto/account/CreateAccountRequest.java`
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/dto/account/UpdateAccountRequest.java`

#### API 设计（与现有 ApiResponse 风格一致）
- `GET /api/accounts?type=EXPENSE`：按类型筛选
- `GET /api/accounts/{id}`：查详情
- `POST /api/accounts`：创建自定义分类（懒创建/内联创建会复用此能力）
- `PUT /api/accounts/{id}`：仅允许改 `name/fixed/necessity`，拒绝 balance 变更

#### 注意事项（必须明确写在实现中）
- 不能用 BeanUtils 直接拷贝请求体到实体（会引入 balance 可写风险）。
- 更新只能字段白名单。

#### 验证
- 登录后调用 `GET /api/accounts` 可返回预置账户。
- 对 `PUT` 请求体携带 balance 时：balance 不应被修改。

#### 注意：与现有异常/返回风格保持一致
- 实现中遇到权限/参数/找不到资源，一律 `throw new BusinessException(code, message)`。
- 避免 `throw new RuntimeException(...)`（会被 GlobalExceptionHandler 以 HTTP 400 返回，且 message 前缀会变成“请求处理异常: ...”，不利于前端提示一致性）。

---

## 阶段 2：Transaction 改造（复式记账 + 不可变 + 并发安全）

> 现状对齐：当前 Transaction 是 `type/category/amount/description/timestamp/userId`，并且 Service/Controller 允许 update/delete。

### Commit 2.1 — `refactor(model): migrate Transaction to double-entry with immutability`

#### 目标
- 把 `Transaction` 模型改为：
  - `fromAccountId/toAccountId`
  - `reversalOfId/reversed`
  - `amount/description/timestamp/userId`
  - （建议加）`createdAt`

#### 文件级改动
修改：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/model/Transaction.java`

修改/新增 DTO（建议“新 DTO，旧 DTO 逐步废弃”）：
- 新增：`finance-tracker/src/main/java/com/gcc_0119/finance_tracker/dto/transaction/CreateTransactionRequest.java`
- 新增：`finance-tracker/src/main/java/com/gcc_0119/finance_tracker/dto/transaction/TransactionView.java`
- 评估是否保留旧 `dto/TransactionDTO.java`：
  - 若要平滑迁移，可先保留，但 Controller 逐步切换使用新 DTO。

#### MongoDB 索引
- 建议在 Transaction 上加索引（方式二选一）：
  1) `@Indexed` 注解（Spring Data 自动建索引）
  2) 单独 `@Configuration` 使用 `MongoTemplate.indexOps()` 初始化索引

索引字段：
- `userId + timestamp`（分页/筛选）
- `fromAccountId`、`toAccountId`、`reversalOfId`

#### 验证
- 编译通过。
- 启动后索引可在 MongoDB 中看到（若开启自动建索引）。

#### 关键代码骨架（供审核）

`model/Transaction.java`（替换现有 type/category 结构）
```java
package com.gcc_0119.finance_tracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "transactions")
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
    private LocalDateTime timestamp;

    @Indexed
    private String reversalOfId;

    private boolean reversed = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

> 审核重点：
> - `amount` 继续保持 DECIMAL128。
> - `timestamp` 是业务时间（用户选择的时间），`createdAt` 是创建时间（审计）。

---

### Commit 2.2 — `refactor(service): rewrite TransactionService with atomic updates`

#### 目标
- **移除** update/delete 行为。
- 新增：
  - `createTransaction`：原子扣款/加款 + 保存交易
  - `reverseTransaction`：冲正交易 + 回滚余额 + 标记原交易 reversed

#### 文件级改动
修改：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/service/TransactionService.java`

新增（如果需要更清晰边界）：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/exception/InsufficientBalanceException.java`（也可直接用 BusinessException）

#### 并发原子更新（关键代码骨架）
- 使用 `MongoTemplate.findAndModify`：
  - fromAccount：条件包含 `balance >= amount` 与 `version == currentVersion`（或仅 version 乐观锁）
  - Update：`inc(balance, -amount)` + `inc(version, 1)`
  - toAccount：`inc(balance, +amount)`（可不带 version，但建议也带 version 形成一致）

> 审核重点：
> - 不能先 `findById` 再 `save` 扣款（会有竞态）。
> - fromAccount 扣款失败要抛 BusinessException（GlobalExceptionHandler 会返回统一 ApiResponse）。

#### 冲正（Reversal）要点
- reversal 交易：`from=original.to`，`to=original.from`，`reversalOfId=original.id`
- 标记原交易：`reversed=true`（并在 Service 中防止二次冲正）
- 冲正也必须走同样的原子余额更新逻辑。

#### 验证
- 并发测试（手工或压测）：同一账户余额 100 同时扣 100 两次，最多一笔成功。
- 冲正后余额与交易标志正确。

#### 扩展性提醒（不影响当前 MVP，但需在答辩时自洽）
- 当前 `balance >= amount` 语义适用于 **ASSET**（资产账户：余额不足则拒绝扣款）。
- 若未来引入 **LIABILITY**（信用卡等负债账户）并允许“透支/授信额度”，`balance >= amount` 将不再适用，需要引入 `creditLimit`（或 allowNegative+limit）并在扣款条件中改为“基于额度的动态校验”。

#### 关键代码骨架（供审核）

新增 DTO：`dto/transaction/CreateTransactionRequest.java`
```java
package com.gcc_0119.finance_tracker.dto.transaction;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTransactionRequest {
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String description;
    private LocalDateTime timestamp; // 可空：空则用 now
}
```

`TransactionService#createTransaction(...)`（原子扣款/加款关键段落）
```java
public Transaction createTransaction(String userId, CreateTransactionRequest req) {
    // 1) 参数校验（amount>0, accountId 非空等）-> BusinessException

    BigDecimal amount = req.getAmount();
    String fromId = req.getFromAccountId();
    String toId = req.getToAccountId();

    // 2) 读取 fromAccount 当前 version（只读，不做扣款）
    Account from = accountRepository.findById(fromId)
        .orElseThrow(() -> new BusinessException(404, "付款账户不存在"));
    if (!from.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权操作付款账户");
    }

    // 3) 原子扣款（关键：balance>=amount && version==currentVersion）
    Query fromQuery = Query.query(Criteria.where("_id").is(fromId)
        .and("userId").is(userId)
        .and("balance").gte(amount)
        .and("version").is(from.getVersion()));

    Update fromUpdate = new Update()
        .inc("balance", amount.negate())
        .inc("version", 1);

    Account fromNew = mongoTemplate.findAndModify(
        fromQuery,
        fromUpdate,
        FindAndModifyOptions.options().returnNew(true),
        Account.class
    );

    if (fromNew == null) {
        throw new BusinessException(400, "余额不足或账户已被修改");
    }

    // 4) 原子加款（toAccount 也要校验 userId）
    Account to = accountRepository.findById(toId)
        .orElseThrow(() -> new BusinessException(404, "收款账户不存在"));
    if (!to.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权操作收款账户");
    }
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(toId).and("userId").is(userId)),
        new Update().inc("balance", amount),
        Account.class
    );

    // 5) 保存交易（不可变：后续不提供 update/delete）
    Transaction tx = new Transaction();
    tx.setUserId(userId);
    tx.setAmount(amount);
    tx.setFromAccountId(fromId);
    tx.setToAccountId(toId);
    tx.setDescription(req.getDescription());
    tx.setTimestamp(req.getTimestamp() != null ? req.getTimestamp() : LocalDateTime.now());
    return transactionRepository.save(tx);
}
```

> 审核重点：
> - `findAndModify` 的条件必须包含 `balance >= amount`，否则会透支。
> - `userId` 必须参与条件（否则可操作他人账户）。
> - `toAccount` 加款也应校验 `userId`。

`reverseTransaction(...)`（冲正关键点）
```java
public Transaction reverseTransaction(String userId, String originalId) {
    Transaction original = transactionRepository.findById(originalId)
        .orElseThrow(() -> new BusinessException(404, "原交易不存在"));
    if (!original.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权冲正该交易");
    }
    if (original.isReversed()) {
        throw new BusinessException(400, "该交易已被冲正");
    }

    // 1) 创建冲正交易（from/to 反转）
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setFromAccountId(original.getToAccountId());
    req.setToAccountId(original.getFromAccountId());
    req.setAmount(original.getAmount());
    req.setDescription("冲正: " + (original.getDescription() == null ? "" : original.getDescription()));
    req.setTimestamp(LocalDateTime.now());
    Transaction reversal = createTransaction(userId, req);
    reversal.setReversalOfId(original.getId());
    Transaction savedReversal = transactionRepository.save(reversal);

    // 2) 标记原交易已冲正（不可变性：不删除，只标记）
    original.setReversed(true);
    transactionRepository.save(original);

    return savedReversal;
}
```

> 注意：严格实现里，“标记原交易 reversed=true”也应做并发保护（避免双击冲正导致两笔 reversal）。审核时可要求：
> - 用 `findAndModify` 对 Transaction 做条件更新（reversed==false 才能置 true），失败则提示已冲正。

---

### Commit 2.3 — `refactor(api): update TransactionController for double-entry`

#### 目标
- Controller 层：
  - 保留查询/创建
  - 新增冲正接口
  - **移除 PUT/DELETE**（返回 405 或直接删掉映射）

#### 文件级改动
修改：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/controller/TransactionController.java`

修改：
- `finance-tracker/src/main/java/com/gcc_0119/finance_tracker/repository/TransactionRepository.java`
  - 移除 `findByType/findByCategory/...`（如果不再使用）
  - 增加按 userId + time range 查询（为分页/筛选准备）

#### API（建议）
- `POST /api/transactions`：创建交易（入参 CreateTransactionRequest）
- `POST /api/transactions/{id}/reverse`：冲正
- `GET /api/transactions`：查询（后续 Commit 5.1 再补分页/筛选）
- `GET /api/transactions/{id}`：详情

#### 验证
- `PUT /api/transactions/{id}` 与 `DELETE /api/transactions/{id}`：应不可用。

#### 关键代码骨架（供审核）

`TransactionController` 新增/调整映射：
```java
@PostMapping
public ApiResponse<Transaction> create(@RequestBody CreateTransactionRequest req) {
    String userId = securityUtils.getCurrentUserId();
    return ApiResponse.success("创建成功", transactionService.createTransaction(userId, req));
}

@PostMapping("/{id}/reverse")
public ApiResponse<Transaction> reverse(@PathVariable String id) {
    String userId = securityUtils.getCurrentUserId();
    return ApiResponse.success("冲正成功", transactionService.reverseTransaction(userId, id));
}

@GetMapping
public ApiResponse<List<Transaction>> list() {
    String userId = securityUtils.getCurrentUserId();
    // 暂时可先保持“按 userId 全量 + 排序”，分页在 Commit 5.1 再加
    return ApiResponse.success(transactionService.listAll(userId));
}
```

关于“返回 405”的实现策略：
- **推荐**：直接删除 `@PutMapping/@DeleteMapping` 方法（请求会自然变成 405）。
- 若必须保留提示信息：实现一个 `@RequestMapping(method={PUT,DELETE})` 返回 BusinessException(405,...)
  - 但要注意：GlobalExceptionHandler 对 BusinessException 返回 HTTP 200，因此前端看到的是 code=405，不是 HTTP 405。
  - 是否接受这种语义，需要你审核确认。

---

## 阶段 3：预算系统（Budget）

### Commit 3.1 — `feat(model): add Budget entity`

#### 文件级改动
新增：
- `model/Budget.java`
- `repository/BudgetRepository.java`

索引：`userId + accountId` 唯一。

#### 关键代码骨架（供审核）
`model/Budget.java`
```java
@Document(collection = "budgets")
@Data
@CompoundIndex(name = "uk_budget_user_account", def = "{'userId': 1, 'accountId': 1}", unique = true)
public class Budget {
    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed
    private String accountId; // 绑定 EXPENSE 分类账户
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal limitAmount;
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

---

### Commit 3.2 — `feat(service): add BudgetService with balance-based calculation`

#### 核心口径（审核点）
- Budget 绑定 `EXPENSE` 分类账户（accountId）。
- Budget 本身不带月份字段：它表达的是一条**永久生效的规则**（例如“餐饮每月上限 2000”）。
- usedAmount（执行口径）必须是**按月**统计：
  - **禁止**用 `EXPENSE` 账户 `balance` 来计算本月花费（balance 代表历史累计，会导致第二个月起预算必然“爆仓”）。
  - usedAmount 必须来自阶段 4 的 `MonthlyReport.categoryExpense[accountId]`（或在 MonthlyReport 未落地前，按月范围聚合 Transaction 作为临时实现）。

> 审核判决：预算“执行口径”一律按月；Budget 仅作为常驻规则，不做“永久累计预算”。

---

### Commit 3.3 — `feat(api): add BudgetController`

新增：
- `controller/BudgetController.java`

---

## 阶段 4：月报中心（MonthlyReport 快照）

### Commit 4.1 — `feat(model): add MonthlyReport snapshot entity`

新增：
- `model/MonthlyReport.java`
- `repository/MonthlyReportRepository.java`

索引：`userId + month` 唯一。

#### 关键代码骨架（供审核）
`model/MonthlyReport.java`（字段可按你的展示需求裁剪）
```java
@Document(collection = "monthly_reports")
@Data
@CompoundIndex(name = "uk_report_user_month", def = "{'userId': 1, 'month': 1}", unique = true)
public class MonthlyReport {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String month; // yyyy-MM

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalIncome = BigDecimal.ZERO;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalExpense = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal balance = BigDecimal.ZERO; // 结余（可在应用层计算后 set）

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal savingRate = BigDecimal.ZERO; // 储蓄率（可由应用层计算后 set）

    private int transactionCount = 0;
    private Map<String, BigDecimal> categoryExpense;
    private Map<String, BigDecimal> categoryIncome;

    private LocalDateTime updatedAt = LocalDateTime.now();
}
```

---

### Commit 4.2 — `feat(service): add MonthlyReportService with incremental updates`

新增：
- `service/MonthlyReportService.java`

修改：
- `TransactionService.createTransaction/reverseTransaction`：在交易成功后调用增量更新

---

## 阶段 5：辅助功能

### Commit 5.1 — `feat(api): add pagination and filtering to TransactionController`

现有对齐：当前 `TransactionService.getAllTransactions` 是全量拉取并排序。

实施：
- 用 MongoTemplate 手写分页（Query + skip/limit + sort），支持 keyword、dateRange、accountId。

#### 关键代码骨架（供审核）
```java
public PageResult<Transaction> query(String userId, int page, int size,
                                    LocalDateTime start, LocalDateTime end,
                                    String keyword, String accountId) {
    Criteria c = Criteria.where("userId").is(userId);
    if (start != null && end != null) {
        c = c.and("timestamp").gte(start).lte(end);
    }
    if (keyword != null && !keyword.isBlank()) {
        c = c.and("description").regex(keyword, "i");
    }
    if (accountId != null && !accountId.isBlank()) {
        c = new Criteria().andOperator(
            Criteria.where("userId").is(userId),
            new Criteria().orOperator(
                Criteria.where("fromAccountId").is(accountId),
                Criteria.where("toAccountId").is(accountId)
            )
        );
    }

    Query q = Query.query(c)
        .with(Sort.by(Sort.Direction.DESC, "timestamp"))
        .skip((long) page * size)
        .limit(size);

    List<Transaction> rows = mongoTemplate.find(q, Transaction.class);
    long total = mongoTemplate.count(Query.query(c), Transaction.class);
    return new PageResult<>(rows, total, page, size);
}
```

---

### Commit 5.2 — `feat(api): add CSV import with preview`

新增：
- `controller/TransactionImportController.java`（或挂在 TransactionController 下）
- `service/CsvImportService.java`

流程：preview（解析、校验、标记重复）→ confirm（批量 createTransaction）。

---

### Commit 5.3 — `refactor(auth): session recovery via /api/users/me (no new verify endpoint)`

#### 核心决策（审核点）
- **不新增** `/api/auth/verify`。
- 会话恢复统一复用已有的 `GET /api/users/me`：
  - 前端 App 启动时调一次 `/api/users/me` 拉取用户信息恢复状态。
  - axios 拦截器遇到 401：清 token、跳登录（现有逻辑可复用）。

#### 与现有安全配置的对齐（审核点）
- 现有 `WebSecurityConfig`：`/api/auth/**` `permitAll()`。
- `/api/users/me` 本身需要鉴权，因此天然具备“verify token”的语义，不会出现“verify 被放行”的陷阱。

---

### Commit 5.4 — `feat(service): add anomaly detection`

在 `createTransaction` 中做异常检测：
- 同“分类账户”历史均值对比（2 倍阈值）
- 返回交易 + warning 字段（需要定义响应 DTO）

---

## 阶段 6：前端改造（finance-tracker-frontend）

### Commit 6.1 — `feat(ui): load categories dynamically`

修改：
- `src/pages/HomePage.jsx`
- 新增：`src/api/account.js`（调用 `/accounts?type=EXPENSE/INCOME`）

目标：分类下拉由后端账户动态加载，并支持“新增分类”（POST /accounts）。

#### 审核点：HomePage 的交易表单字段需要改变
- 现有表单字段：`type/category/amount/description/timestamp`
- 新表单字段：`fromAccountId/toAccountId/amount/description/timestamp`
  - 对“记一笔支出”：from=ASSET(现金)，to=EXPENSE(分类账户)
  - 对“记一笔收入”：from=INCOME(分类账户)，to=ASSET(现金)
  - 映射逻辑放在前端（表单提交时组装），后端接口层**只接受** `fromAccountId/toAccountId`。

> 审核判决：**坚决封杀** `type/category` 作为 API 入参。
> - 后端不提供“根据 type/category 推断账户”的兼容层。
> - 前端必须先加载账户列表并按类型分组展示，让用户显式选择（或由 UI 默认选择）from/to。

---

### Commit 6.2 — `feat(ui): migrate budget page to backend API`

修改：
- `src/pages/StatisticsPage.jsx`：预算不再写 localStorage
- 新增：`src/api/budget.js`

---

### Commit 6.3 — `feat(ui): add monthly report page`

新增：
- `src/pages/MonthlyReportPage.jsx`
- `src/api/report.js`

---

### Commit 6.4 — `feat(ui): add CSV import page`

新增：
- `src/pages/ImportPage.jsx`
- 对应路由与 API

---

### Commit 6.5 — `feat(auth): implement session recovery`

修改：
- `src/AuthContext.jsx`：启动时调用 `/api/users/me` 恢复用户（不再引入 verify 端点）
- `src/utils/request.js`：401 已有统一处理，可保持

---

## 阶段 7：数据迁移

### Commit 7.1 — `feat(migration): add script to convert legacy data`

新增：
- `scripts/migration/legacy-to-double-entry.js`（Node）或 `scripts/migration/LegacyMigrationRunner.java`

核心：
- 为每个用户创建默认 ASSET/EQUITY/分类账户
- 把旧交易 type/category 转换为 from/to：
  - expense：from=ASSET(现金)，to=EXPENSE(对应分类)
  - income：from=INCOME(对应分类)，to=ASSET(现金)
- 回放交易以生成 balance/version（version 可从 0 累加）

---

## 最终一致性检查清单（你审核用）

1) 是否彻底移除 update/delete（后端与前端 UI）
2) 余额是否只由 findAndModify 原子更新改变（禁止任何“直接写 balance”接口）
3) 冲正是否可重复调用（必须防二次冲正）
4) GlobalExceptionHandler 的返回风格是否保持一致（BusinessException → ApiResponse(code!=200)）
5) 会话恢复是否统一复用 `/api/users/me`（而非新增 `/api/auth/verify`），并与 401 拦截器契合
6) 预算口径是否按月：usedAmount 不得来自 EXPENSE 账户 balance，必须来自 MonthlyReport（或按月聚合 Transaction 作为过渡）
7) API 契约是否已彻底移除 type/category 入参（前端必须提交 fromAccountId/toAccountId）

8) AdminController/前端 Admin 页是否需要同步适配
- 现有 `AdminController`：直接返回 `Transaction` 列表（字段会在 2.1 发生结构性变化）。
- 审核建议：在 Commit 2.3 或 5.x 中补一个“管理员交易视图 DTO”，避免前端/接口直接暴露新字段造成 UI 崩溃。
