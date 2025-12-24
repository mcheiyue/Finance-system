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

  const totalExpense = monthData.filter(t => t.type === 'expense').reduce((sum, t) => sum + Number(t.amount), 0);
  const totalIncome = monthData.filter(t => t.type === 'income').reduce((sum, t) => sum + Number(t.amount), 0);

  return `
- 当前月份：${currentMonth}
- 本月总支出：¥${totalExpense.toFixed(2)}
- 本月总收入：¥${totalIncome.toFixed(2)}
  `;
};

const buildMessages = (history, userMessage, systemPrompt) => {
  return [
    { role: 'system', content: systemPrompt },
    ...history.slice(-5).map(m => ({
      role: m.role === 'ai' || m.role === 'assistant' ? 'assistant' : 'user',
      content: String(m.content).replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '').replace(/\[FILTER\][\s\S]*?\[\/FILTER\]/g, '').trim()
    })),
    { role: 'user', content: userMessage }
  ];
};

export const callAiApi = async (userMessage, history, config, contextData) => {
  if (!config.apiKey) throw new Error('请先配置 API Key');

  const sanitizedData = sanitizeTransactions(contextData);
  const summary = getFinancialSummary(contextData);
  const today = dayjs().format('YYYY-MM-DD');

const systemPrompt = `
# Role
你是一位专业的私人理财助手。
当前日期：${today}

# Context
${summary}
最近账单：${JSON.stringify(sanitizedData)}

# 核心指令 (Action Protocol)

1. **记账指令 (Batch Transaction)**：
   [ACTION]
   [
     {"type":"expense","amount":30,"category":"交通","description":"打车去公司"}
   ]
   [/ACTION]
   - category: 必须从 [${EXPENSE_CATEGORIES.join(', ')}] 或 [${INCOME_CATEGORIES.join(', ')}] 中选择。

2. **筛选指令 (Filter Protocol)**：
   当用户想要【查找】、【搜索】或【查看】记录时：
   [FILTER]
   {
     "type": "expense",      
     "keyword": "交通",       
     "startDate": "2025-12-22", 
     "endDate": "2025-12-28"    
   }
   [/FILTER]
   - type: "income", "expense" 或 "all"
   - keyword: 搜索关键词。**注意：如果用户按【分类】筛选（如"查餐饮"），请务必将分类名称填入此字段**。
   - 日期：YYYY-MM-DD 格式，基于当前日期自动推算。

# 思维链处理
在生成指令前，请执行以下逻辑（不需要输出解释）：
1. 自动计算分摊金额（如"AA"）。
2. 自动转换汇率。
3. 识别多意图。

禁止输出 markdown 代码块，直接输出标签。
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
    throw new Error(error.response?.data?.error?.message || error.message || '网络请求失败');
  }
};