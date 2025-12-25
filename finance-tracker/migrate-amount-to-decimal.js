// MongoDB 数据迁移脚本
// 用途：将 transactions 集合中的 amount 字段从字符串类型转换为 Decimal128 类型
// 使用方法：在 MongoDB Shell 或 Compass 中执行此脚本

// 方法一：使用 mongosh 命令行执行
// mongosh "mongodb://localhost:27017/your_database_name" migrate-amount-to-decimal.js

// 方法二：在 MongoDB Compass 中打开 Shell，粘贴以下代码执行

// 查找所有 amount 字段为字符串类型的文档并转换
db.transactions.find({ amount: { $type: "string" } }).forEach(function(doc) {
    try {
        db.transactions.updateOne(
            { _id: doc._id },
            { $set: { amount: NumberDecimal(doc.amount) } }
        );
        print("已转换文档: " + doc._id);
    } catch (e) {
        print("转换失败 - 文档ID: " + doc._id + ", 错误: " + e.message);
    }
});

// 查找所有 amount 字段为 double/int 类型的文档并转换为 Decimal128
db.transactions.find({ 
    $or: [
        { amount: { $type: "double" } },
        { amount: { $type: "int" } },
        { amount: { $type: "long" } }
    ]
}).forEach(function(doc) {
    try {
        db.transactions.updateOne(
            { _id: doc._id },
            { $set: { amount: NumberDecimal(doc.amount.toString()) } }
        );
        print("已转换文档: " + doc._id);
    } catch (e) {
        print("转换失败 - 文档ID: " + doc._id + ", 错误: " + e.message);
    }
});

print("迁移完成！");

// 验证迁移结果
print("\n=== 验证迁移结果 ===");
print("Decimal128 类型的 amount 数量: " + db.transactions.countDocuments({ amount: { $type: "decimal" } }));
print("字符串类型的 amount 数量: " + db.transactions.countDocuments({ amount: { $type: "string" } }));
print("其他类型的 amount 数量: " + db.transactions.countDocuments({ 
    amount: { $exists: true },
    $nor: [
        { amount: { $type: "decimal" } },
        { amount: { $type: "string" } }
    ]
}));
