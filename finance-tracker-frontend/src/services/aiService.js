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
        .replace(/\[THOUGHT\][\s\S]*?\[\/THOUGHT\]/g, '') 
        .replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '[已记账]') 
        .replace(/\[FILTER\][\s\S]*?\[\/FILTER\]/g, '[已执行查询]')
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

const systemPrompt = `
# Role
你是一位专业的私人理财助手。
**当前时间锚点**：${timeAnchor}

# Context
- ${summary}
- 最近 50 笔交易：${JSON.stringify(sanitizedData)}
- 注意：你只能看到最近 50 笔。如需查询更早数据，请使用 [FILTER] 指令，不要直接回答“没有记录”。

# 协议 (Protocols)

请先在 **[THOUGHT]** 标签中进行推理，然后根据情况输出 **[ACTION]**、**[FILTER]** 或直接回复文本。

## 1. 记账 (Action)
当信息（金额、分类）完整时生成。
分类必须属于：[${EXPENSE_CATEGORIES.join(', ')}] 或 [${INCOME_CATEGORIES.join(', ')}]。
**模糊匹配**："肯德基"->"餐饮", "打车"->"交通"。

格式：
[ACTION]
[{"type":"expense","amount":30,"category":"餐饮","description":"肯德基"}]
[/ACTION]

## 2. 查账 (Filter)
当用户询问历史记录时。
**时间推算规则**：基于“当前时间锚点”进行推算。
例如：若今天是周三，问“上周五”，则计算出上周五的具体日期填入。

格式：
[FILTER]
{"type": "expense", "startDate": "2025-XX-XX", "endDate": "2025-XX-XX"}
[/FILTER]

## 3. 追问 (Ask)
当信息缺失（如只说了金额没说分类）时，请不要生成指令，直接像真人一样追问用户。

# 示例
用户：上周三吃饭花了多少？
回复：
[THOUGHT]
用户意图是查账。
当前是 ${timeAnchor}。推算出上周三是 XXXX-XX-XX。
分类关键词：吃饭 -> "餐饮"。
[/THOUGHT]
[FILTER]
{"type": "expense", "keyword": "餐饮", "startDate": "...", "endDate": "..."}
[/FILTER]
`;

  const aiInstance = axios.create();
  const messages = buildMessages(history, userMessage, systemPrompt);

  try {
    const response = await aiInstance.post(`${config.baseUrl}/chat/completions`, {
      model: config.model,
      messages: messages,
      temperature: 0.3 
    }, {
      headers: {
        'Authorization': `Bearer ${config.apiKey.trim()}`,
        'Content-Type': 'application/json'
      }
    });

            const choices = response.data?.choices;
    if (!choices || choices.length === 0) {
      console.error('AI 原始响应异常:', response.data);
      throw new Error('AI 返回内容为空。请检查：1. 模型名称是否正确 (建议 gemini-1.5-flash)；2. 是否触发了安全拦截。');
    }

    return choices[0].message.content;
  } catch (error) {
    console.error('AI Service Error:', error);
    const apiErrorMsg = error.response?.data?.error?.message || error.message;
    throw new Error(apiErrorMsg || '网络请求失败');
  }
};