// MongoDB 数据迁移脚本
// 用途：将旧版 type/category 交易模型迁移为复式记账模型（fromAccountId/toAccountId）
// 使用方法：mongosh "mongodb://localhost:27017/finance_db" migrate-to-double-entry.js
//
// 迁移逻辑：
// 1. 为每个用户创建系统账户（ASSET/EQUITY/EXPENSE/INCOME）
// 2. 将旧 Transaction 的 type+category 转换为 fromAccountId+toAccountId
// 3. 计算并设置各账户的 balance
//
// 注意：运行前请备份数据库！
// mongodump --db=finance_db --out=./backup_$(date +%Y%m%d)

print("=== 复式记账数据迁移 ===\n");

// ========== 第一步：为每个用户创建系统账户 ==========

print("第一步：创建系统账户...");

var users = db.users.find().toArray();
print("找到 " + users.length + " 个用户");

var expenseCategories = ["餐饮", "交通", "购物", "娱乐", "住房", "医疗", "教育", "其他"];
var incomeCategories = ["工资", "兼职", "投资", "红包", "其他"];

users.forEach(function(user) {
    var userId = user._id.toString();
    
    // 检查是否已有系统账户
    var existingAccounts = db.accounts.countDocuments({ userId: userId, isSystem: true });
    if (existingAccounts > 0) {
        print("用户 " + user.username + " 已有系统账户，跳过");
        return;
    }
    
    var now = new Date();
    var accounts = [];
    
    // ASSET 账户
    accounts.push({
        userId: userId,
        name: "默认现金",
        type: "ASSET",
        balance: NumberDecimal("0"),
        version: 0,
        isSystem: true,
        fixed: false,
        necessity: "NECESSARY",
        createdAt: now
    });
    
    // EQUITY 账户
    accounts.push({
        userId: userId,
        name: "Opening-Balance",
        type: "EQUITY",
        balance: NumberDecimal("0"),
        version: 0,
        isSystem: true,
        fixed: false,
        necessity: "NECESSARY",
        createdAt: now
    });
    
    // EXPENSE 账户
    expenseCategories.forEach(function(cat) {
        accounts.push({
            userId: userId,
            name: cat,
            type: "EXPENSE",
            balance: NumberDecimal("0"),
            version: 0,
            isSystem: true,
            fixed: false,
            necessity: "NECESSARY",
            createdAt: now
        });
    });
    
    // INCOME 账户
    incomeCategories.forEach(function(cat) {
        accounts.push({
            userId: userId,
            name: cat,
            type: "INCOME",
            balance: NumberDecimal("0"),
            version: 0,
            isSystem: true,
            fixed: false,
            necessity: "NECESSARY",
            createdAt: now
        });
    });
    
    if (accounts.length > 0) {
        db.accounts.insertMany(accounts);
        print("用户 " + user.username + ": 创建了 " + accounts.length + " 个系统账户");
    }
});

print("\n第二步：转换旧交易数据...");

// ========== 第二步：转换旧交易 ==========

// 查找所有旧格式交易（有 type 字段，无 fromAccountId 字段）
var oldTransactions = db.transactions.find({
    type: { $exists: true },
    fromAccountId: { $exists: false }
}).toArray();

print("找到 " + oldTransactions.length + " 笔旧格式交易");

var converted = 0;
var skipped = 0;
var errors = 0;

// 为每个用户构建账户映射
var userAccountMaps = {};

users.forEach(function(user) {
    var userId = user._id.toString();
    var accounts = db.accounts.find({ userId: userId }).toArray();
    
    var accountMap = {};
    accounts.forEach(function(acc) {
        accountMap[acc.type + ":" + acc.name] = acc._id.toString();
    });
    
    userAccountMaps[userId] = accountMap;
});

oldTransactions.forEach(function(tx) {
    try {
        var userId = tx.userId;
        var accountMap = userAccountMaps[userId];
        
        if (!accountMap) {
            print("跳过：找不到用户 " + userId + " 的账户映射");
            skipped++;
            return;
        }
        
        var assetAccountId = accountMap["ASSET:默认现金"];
        var fromAccountId, toAccountId;
        
        if (tx.type === "expense") {
            // 支出：ASSET -> EXPENSE
            fromAccountId = assetAccountId;
            toAccountId = accountMap["EXPENSE:" + tx.category];
        } else if (tx.type === "income") {
            // 收入：INCOME -> ASSET
            fromAccountId = accountMap["INCOME:" + tx.category];
            toAccountId = assetAccountId;
        } else {
            print("跳过：未知类型 " + tx.type + " (交易ID: " + tx._id + ")");
            skipped++;
            return;
        }
        
        if (!fromAccountId || !toAccountId) {
            print("跳过：找不到账户映射 (交易ID: " + tx._id + ", type: " + tx.type + ", category: " + tx.category + ")");
            skipped++;
            return;
        }
        
        // 更新交易记录
        db.transactions.updateOne(
            { _id: tx._id },
            {
                $set: {
                    fromAccountId: fromAccountId,
                    toAccountId: toAccountId,
                    reversed: false,
                    createdAt: tx.timestamp || new Date()
                },
                $unset: { type: "", category: "" }
            }
        );
        
        converted++;
    } catch (e) {
        print("转换失败 - 交易ID: " + tx._id + ", 错误: " + e.message);
        errors++;
    }
});

print("转换完成: 成功 " + converted + ", 跳过 " + skipped + ", 失败 " + errors);

// ========== 第三步：计算账户余额 ==========

print("\n第三步：计算账户余额...");

users.forEach(function(user) {
    var userId = user._id.toString();
    var accounts = db.accounts.find({ userId: userId }).toArray();
    
    accounts.forEach(function(account) {
        var accountId = account._id.toString();
        
        // 计算收入（作为 toAccount）
        var incomeResult = db.transactions.aggregate([
            {
                $match: {
                    toAccountId: accountId,
                    reversed: { $ne: true }
                }
            },
            {
                $group: {
                    _id: null,
                    total: { $sum: { $toDouble: "$amount" } }
                }
            }
        ]).toArray();
        
        // 计算支出（作为 fromAccount）
        var expenseResult = db.transactions.aggregate([
            {
                $match: {
                    fromAccountId: accountId,
                    reversed: { $ne: true }
                }
            },
            {
                $group: {
                    _id: null,
                    total: { $sum: { $toDouble: "$amount" } }
                }
            }
        ]).toArray();
        
        var income = incomeResult.length > 0 ? incomeResult[0].total : 0;
        var expense = expenseResult.length > 0 ? expenseResult[0].total : 0;
        var balance = income - expense;
        
        db.accounts.updateOne(
            { _id: account._id },
            { $set: { balance: NumberDecimal(balance.toString()) } }
        );
    });
    
    print("用户 " + user.username + ": 余额计算完成");
});

// ========== 验证结果 ==========

print("\n=== 迁移验证 ===");

var totalAccounts = db.accounts.countDocuments({});
var totalTransactions = db.transactions.countDocuments({});
var newFormatTx = db.transactions.countDocuments({ fromAccountId: { $exists: true } });
var oldFormatTx = db.transactions.countDocuments({ type: { $exists: true } });

print("总账户数: " + totalAccounts);
print("总交易数: " + totalTransactions);
print("新格式交易: " + newFormatTx);
print("旧格式交易残留: " + oldFormatTx);

if (oldFormatTx === 0) {
    print("\n✅ 迁移成功！所有交易已转换为复式记账格式。");
} else {
    print("\n⚠️ 还有 " + oldFormatTx + " 笔旧格式交易未转换，请检查日志。");
}
