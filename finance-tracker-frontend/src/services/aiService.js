import axios from 'axios';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn'; 
import { EXPENSE_CATEGORIES, INCOME_CATEGORIES } from '../constants';

dayjs.locale('zh-cn');

export const sanitizeTransactions = (data) => {
  return (data || []).slice(0, 50).map(item => ({
    type: item.type === 'income' ? '收入' : '支出',
    category: item.category,
    amount: item.amount,
    description: item.description,
    date: dayjs(item.timestamp).format('YYYY-MM-DD')
  }));
};

const getFinancialSummary = (transactions) => {
  if (!transactions || transactions.length === 0) return '暂无交易数据';
  const currentMonth = dayjs().format('YYYY-MM');
  const monthData = transactions.filter(t => dayjs(t.timestamp).format('YYYY-MM') === currentMonth);
  
  const totalExpense = monthData.filter(t => t.type === 'expense').reduce((sum, t) => sum + Number(t.amount), 0);
  const totalIncome = monthData.filter(t => t.type === 'income').reduce((sum, t) => sum + Number(t.amount), 0);

  return `当前月份(${currentMonth})概况：总支出 ¥${totalExpense.toFixed(2)}，总收入 ¥${totalIncome.toFixed(2)}`;
};

const buildMessages = (history, userMessage, systemPrompt) => {
  return [
    { role: 'system', content: systemPrompt },
    ...history.slice(-10).map(m => ({
      role: m.role === 'ai' || m.role === 'assistant' ? 'assistant' : 'user',
      content: String(m.content)
        // 清理历史记录，节省 Token
        .replace(/\[THOUGHT\][\s\S]*?\[\/THOUGHT\]/g, '') 
        .replace(/\[REPLY\]/g, '')
        .trim()
    })),
    { role: 'user', content: userMessage }
  ];
};

export const callAiApi = async (userMessage, history, config, contextData) => {
  if (!config.apiKey) throw new Error('请先配置 API Key');

  const sanitizedData = sanitizeTransactions(contextData);
  const summary = getFinancialSummary(contextData);
  const now = dayjs();
  const timeAnchor = `${now.format('YYYY-MM-DD HH:mm')} (${now.format('dddd')})`;

  // ==================== 【重点：引入 [REPLY] 强制分割】 ====================
  const systemPrompt = `
# Role
你是一位专业的私人理财助手。
**当前时间锚点**：${timeAnchor}

# Context
- ${summary}
- 最近 50 笔交易：${JSON.stringify(sanitizedData)}

# 核心协议 (Protocols)

## 1. 思考与回复 (Mandatory Flow)
你必须严格遵守以下输出结构：
1. 先在 **[THOUGHT]** 标签内进行思考（计算、筛选、逻辑推理）。
2. 然后使用 **[REPLY]** 标签开始你的最终回复。
3. **[REPLY]** 标签后的内容才是给用户看的，之前的所有内容都会被系统隐藏。

**正确结构示例：**
[THOUGHT]
用户问上周支出。定位日期范围... 遍历数据发现3笔... 总和400。
[/THOUGHT]
[REPLY]
根据记录，您上周共支出了 400 元。

## 2. 功能指令
- **记账**：在 [REPLY] 之后输出 [ACTION] JSON。
- **查账**：在 [REPLY] 之后输出 [FILTER] JSON。

# 🚫 严格禁令
1. **严禁**在 [REPLY] 标签之前输出任何给用户的回复。
2. **严禁**在 [REPLY] 标签之后输出类似 \`{"type":"expense"...}\` 的原始数据行，除非是被 [ACTION] 包裹。
3. **严禁**在 [REPLY] 之后列出计算步骤（如“第一笔...第二笔...”），只给结论。

# 示例
用户：上周三吃饭花了多少？
回复：
[THOUGHT]
1. 确定日期：2025-12-17。
2. 筛选分类：餐饮。
3. 遍历发现1笔：402.45。
[/THOUGHT]
[REPLY]
根据记录，您上周三（12月17日）在餐饮方面共消费了 402.45 元。
`;
  
  const aiInstance = axios.create();
  const messages = buildMessages(history, userMessage, systemPrompt);

  try {
    const response = await aiInstance.post(`${config.baseUrl}/chat/completions`, {
      model: config.model,
      messages: messages,
      temperature: 0.3 // 低温保证格式稳定
    }, {
      headers: {
        'Authorization': `Bearer ${config.apiKey.trim()}`,
        'Content-Type': 'application/json'
      }
    });

    const choices = response.data?.choices;
    if (!choices || choices.length === 0) {
      throw new Error('AI 返回内容为空。请检查模型名称是否正确。');
    }

    return choices[0].message.content;
  } catch (error) {
    console.error('AI Service Error:', error);
    const apiErrorMsg = error.response?.data?.error?.message || error.message;
    throw new Error(apiErrorMsg || '网络请求失败');
  }
};