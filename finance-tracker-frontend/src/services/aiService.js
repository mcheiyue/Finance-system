import axios from 'axios';
import dayjs from 'dayjs';
import { EXPENSE_CATEGORIES, INCOME_CATEGORIES } from '../constants';

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

  if (monthData.length === 0) return `当前月份 (${currentMonth}) 暂无数据`;

  const totalExpense = monthData
    .filter(t => t.type === 'expense')
    .reduce((sum, t) => sum + Number(t.amount), 0);

  const totalIncome = monthData
    .filter(t => t.type === 'income')
    .reduce((sum, t) => sum + Number(t.amount), 0);

  const catMap = {};
  monthData.filter(t => t.type === 'expense').forEach(t => {
    catMap[t.category] = (catMap[t.category] || 0) + Number(t.amount);
  });
  const sortedCats = Object.entries(catMap).sort((a, b) => b[1] - a[1]);
  const topCategory = sortedCats.length > 0
    ? `${sortedCats[0][0]} (¥${sortedCats[0][1].toFixed(2)})`
    : '无';

  return `
- 当前月份：${currentMonth}
- 本月总支出：¥${totalExpense.toFixed(2)}
- 本月总收入：¥${totalIncome.toFixed(2)}
- 支出最高分类：${topCategory}
- 结余：¥${(totalIncome - totalExpense).toFixed(2)}
  `;
};

const buildMessages = (history, userMessage, systemPrompt) => {
  return [
    { role: 'system', content: systemPrompt },
    ...history.slice(-5).map(m => ({
      role: m.role === 'ai' || m.role === 'assistant' ? 'assistant' : 'user',
      content: String(m.content).replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '').trim()
    })),
    { role: 'user', content: userMessage }
  ];
};

export const callAiApi = async (userMessage, history, config, contextData) => {
  if (!config.apiKey) throw new Error('请先配置 API Key');

  const sanitizedData = sanitizeTransactions(contextData);
  const summary = getFinancialSummary(contextData);

  const systemPrompt = `
# Role
你是一位专业的私人理财助手。

# Context (实时财务状况)
${summary}

# Recent Transactions (最近流水)
${JSON.stringify(sanitizedData)}

# 核心指令 (Action Protocol)
当且仅当用户明确想要【记账】或【新增记录】时（例如："我刚才打车花了30"），你必须在回复的末尾，严格按照下方格式输出 JSON 指令：

[ACTION]{"type":"expense","amount":0,"category":"分类","description":"备注"}[/ACTION]

# 约束
1. type 只能是 "expense" (支出) 或 "income" (收入)。
2. category 必须从以下列表中选择最接近的一个：
   - 支出：${EXPENSE_CATEGORIES.join(', ')}
   - 收入：${INCOME_CATEGORIES.join(', ')}
3. description 是简短的备注。
4. 如果是普通聊天或查询分析，严禁输出 [ACTION] 标签。
5. 不要输出 markdown 代码块（如 \`\`\`json），直接输出 [ACTION]...[/ACTION]。
`;

  const aiInstance = axios.create();
  const messages = buildMessages(history, userMessage, systemPrompt);

  try {
    const response = await aiInstance.post(`${config.baseUrl}/chat/completions`, {
      model: config.model,
      messages: messages,
      temperature: 0.7
    }, {
      headers: {
        'Authorization': `Bearer ${config.apiKey.trim()}`,
        'Content-Type': 'application/json'
      }
    });

    return response.data.choices[0].message.content;
  } catch (error) {
    console.error('AI Service Error:', error);
    if (error.response) {
      throw new Error(`请求失败 (${error.response.status}): ${error.response.data?.error?.message || '未知错误'}`);
    }
    throw new Error(error.message || '网络连接失败');
  }
};