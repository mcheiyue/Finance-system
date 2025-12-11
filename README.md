# 💰 Personal Finance Tracker (个人理财记账系统)

> 基于 Spring Boot 3 + React 18 + MongoDB 的现代化全栈记账应用。
> 采用 "Minimal Mono" 极简设计语言，支持深色模式与 RBAC 权限管理。

## 📖 项目介绍
[cite_start]本项目旨在构建一个安全、高效、美观的多用户在线记账平台。不仅解决了传统记账方式数据易丢失、难以统计的痛点 [cite: 35][cite_start]，还在技术上实现了完全的前后端分离、JWT 无状态认证以及数据隔离 [cite: 36, 41]。

[cite_start]UI 设计摒弃了传统的繁杂风格，采用 Ant Design 5.0 构建了 "极简灰" 界面，并完美适配 Dark Mode [cite: 52, 53]。

## ✨ 核心功能

### [cite_start]1. 用户认证与安全 (Authentication) [cite: 83]
- [cite_start]**JWT 认证**：无状态会话管理，自动处理 Token 续期与拦截 [cite: 85]。
- [cite_start]**安全防护**：密码采用 BCrypt 强哈希加密存储 [cite: 46]。
- [cite_start]**RBAC 权限**：区分“普通用户”与“管理员”，支持动态权限路由 [cite: 45]。

### [cite_start]2. 账单管理 (Ledger Manager) [cite: 87]
- [cite_start]**全能记录**：支持收支记录的增删改查 (CRUD) 及日期补录 [cite: 88]。
- [cite_start]**智能筛选**：支持按日期范围、收支类型、关键字组合检索 [cite: 89]。
- [cite_start]**数据导出**：支持一键导出 CSV 格式报表，便于离线备份 [cite: 50]。

### [cite_start]3. 数据可视化 (Data Visualization) [cite: 91]
- [cite_start]**收支构成**：基于 Ant Design Charts 的环形图展示分类占比 [cite: 92]。
- [cite_start]**资产走势**：折线图展示近 30 天净资产波动，交互体验流畅 [cite: 93]。
- [cite_start]**预算监控**：实时对比预算与支出，超支自动高亮警示 [cite: 94]。

### [cite_start]4. 系统管理 (Admin Dashboard) [cite: 95]
- [cite_start]**全站审计**：管理员可查看全站用户列表及流水记录 [cite: 119, 120]。
- [cite_start]**级联删除**：支持强制注销违规用户，并自动清理其关联的所有账单数据 [cite: 97]。

## [cite_start]🛠 技术栈 [cite: 56]

| 类别 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.5.8 | 核心业务逻辑 |
| **Database** | MongoDB | [cite_start]NoSQL 文档型数据库，无需复杂 Join [cite: 106] |
| **Security** | Spring Security + JJWT | 标准化安全框架 |
| **Frontend** | React 18 + Vite | 高性能单页应用 (SPA) |
| **UI Framework** | Ant Design 5.0 | 适配深色模式的现代化组件库 |
| **Charts** | G2Plot (Ant Design Charts) | 数据可视化 |

## 📂 目录结构
```text
FINANCE_SYSTEM/
├── finance-tracker/              # 后端工程 (Spring Boot)
│   ├── src/main/java/com/gcc_0119/finance_tracker/
│   │   ├── config/               # CORS, Security 配置
│   │   ├── controller/           # RESTful API 接口
│   │   ├── model/                # MongoDB 文档实体 (User, Transaction)
│   │   ├── repository/           # 数据访问层
│   │   ├── security/             # JWT 过滤器与工具类
│   │   └── service/              # 业务逻辑 (数据隔离实现)
│   └── pom.xml                   # Maven 依赖管理
│
└── finance-tracker-frontend/     # 前端工程 (React + Vite)
    ├── src/
    │   ├── pages/                # 页面组件 (Home, Login, Stats, Admin)
    │   ├── AuthContext.jsx       # 登录态全局管理
    │   ├── ThemeContext.jsx      # 深色模式主题管理
    │   └── main.jsx              # 入口文件
    └── vite.config.js            # Vite 构建配置