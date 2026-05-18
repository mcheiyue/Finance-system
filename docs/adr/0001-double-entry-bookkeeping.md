# 复式记账模型（Double-Entry Bookkeeping）

采用复式记账法记录所有资金流动。每笔交易由 `fromAccountId`（资金流出）和 `toAccountId`（资金流入）组成，利用 MongoDB 单文档原子性保证一致性，避免多文档事务的部署复杂度。

## 考虑的选项

**选项 1：单式记账 + 独立 Transfer 集合**
- Transaction 只记录收支（accountId + type），转账用单独的 Transfer 集合
- 优点：简单直观
- 缺点：两套集合，统计"本月所有资金变动"需要合并查询；转账的一致性需要多文档事务

**选项 2：单 Transaction 集合 + type 字段（INCOME/EXPENSE/TRANSFER）**
- 转账用两笔 Transaction 记录（TRANSFER_OUT + TRANSFER_IN），用 transferId 关联
- 优点：所有资金流动在一个集合
- 缺点：两笔独立文档存在"单边账"风险——第一笔写入成功、第二笔失败时资金蒸发

**选项 3：复式记账 + 五大根基账户（采用）**
- 所有交易统一用 fromAccountId + toAccountId
- 引入五大根基账户：ASSET（资产）、LIABILITY（负债）、EQUITY（权益）、EXPENSE（费用）、INCOME（收入）
- 支出 = 从资产账户转到费用虚拟账户，收入 = 从收入虚拟账户转到资产账户
- 初始余额 = 从权益账户（Opening-Balance）转到资产账户
- 负债消费 = 从负债账户转到费用虚拟账户，还款 = 从资产账户转到负债账户
- 优点：语义纯粹、查询统一（`fromAccountId == X OR toAccountId == X`）、单文档原子性天然保证一致性、符合会计标准平账公式
- 缺点：需要管理虚拟账户，概念理解成本稍高

## 决策理由

1. **单文档原子性**：MongoDB 单个文档的写入是原子的，不需要多文档事务，不需要副本集架构支持
2. **查询统一**：查任何账户（实体或虚拟）的流水，条件永远是 `fromAccountId == X OR toAccountId == X`
3. **语义纯粹**：一切资金流动皆为转账，没有特殊类型分支
4. **避免单边账**：转账在同一个文档里，要么成功要么失败，不会出现资金蒸发

## 下游影响

- 需要 Account 实体，区分五种类型：ASSET、LIABILITY、EQUITY、EXPENSE、INCOME
- 用户注册时需要预置虚拟账户（餐饮、交通、工资等）和权益账户（Opening-Balance）
- 历史数据需要迁移脚本转换格式
- 前端提交交易时需要后端自动映射分类到虚拟账户
- 平账公式：Assets + Liabilities + Equity + Income + Expenses = 0
