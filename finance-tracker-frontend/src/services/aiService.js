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
        .replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '[已执行记账]') 
        .replace(/\[FILTER\][\s\S]*?\[\/FILTER\]/g, '[已执行筛选]')
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
- 最近 50 笔交易记录（AI视界）：${JSON.stringify(sanitizedData)}
- **注意**：你只能看到最近 50 笔数据。如果用户询问更早的数据，请使用 [FILTER] 指令进行查找，不要直接回答“没有记录”。

# 核心能力与协议

你需要先进行**思维链推理 ([THOUGHT])**，然后根据情况输出 **[ACTION]**、**[FILTER]** 或 **[ASK]**。

## 1. 记账 (Batch Transaction)
当用户意图明确且包含“金额”、“分类”时使用。
分类必须属于：[${EXPENSE_CATEGORIES.join(', ')}] 或 [${INCOME_CATEGORIES.join(', ')}]。

**模糊分类映射规则 (Few-Shot)**：
- "买菜"、"吃肯德基" -> "餐饮"
- "交话费"、"买游戏" -> "娱乐" 或 "其他"
- "打车"、"加油" -> "交通"

**指令格式**：
[ACTION]
[{"type":"expense","amount":30,"category":"餐饮","description":"肯德基"}]
[/ACTION]

## 2. 查账/筛选 (Filter Protocol)
当用户询问历史数据、统计数据或特定记录时。
**日期处理规则**：
- 基于“当前时间锚点”推算相对日期。
- 示例：如果今天是 2025-12-24 (周三)，用户问“上周末”，则 startDate="2025-12-20", endDate="2025-12-21"。

**指令格式**：
[FILTER]
{"type": "expense", "keyword": "餐饮", "startDate": "2025-12-01", "endDate": "2025-12-31"}
[/FILTER]

## 3. 追问 (Ask Protocol)
当用户意图模糊或缺少关键信息（如只说了“我花了钱”但没说多少），请不要瞎编，而是追问。
**直接回复文本即可，不需要特殊标签。**

# 思维链 (Chain of Thought)
在输出任何 Action 之前，必须先在 [THOUGHT] 标签中进行推理：
1. **意图识别**：用户是想记账、查账还是闲聊？
2. **信息完整性**：缺金额吗？缺分类吗？需要追问吗？
3. **数据处理**：
   - 如果是 AA 制，计算：总额 / 人数。
   - 如果是外币，预估汇率转换（或备注）。
   - 如果是相对时间（"上个月"），根据当前锚点计算具体日期范围。

# 示例
用户：我和小明吃火锅花了200，AA
回复：
[THOUGHT]
用户意图是记账。总金额200，AA制（2人），每人100。
描述包含"火锅"，归类为"餐饮"。
信息完整，生成 Action。
[/THOUGHT]
[ACTION]
[{"type":"expense","amount":100,"category":"餐饮","description":"火锅(AA)"}]
[/ACTION]

用户：上个月花了多少钱？
回复：
[THOUGHT]
用户查账。今天是 ${now.format('YYYY-MM-DD')}。上个月是 ${now.subtract(1, 'month').format('YYYY-MM')}。
生成 Filter 指令以展示上月数据。
[/THOUGHT]
[FILTER]
{"type": "expense", "startDate": "${now.subtract(1, 'month').startOf('month').format('YYYY-MM-DD')}", "endDate": "${now.subtract(1, 'month').endOf('month').format('YYYY-MM-DD')}"}
[/FILTER]
已为您筛选出上个月的账单。
`;

  const aiInstance = axios.create();
  const messages = buildMessages(history, userMessage, systemPrompt);

  try {
    const response = await aiInstance.post(`${config.baseUrl}/chat/completions`, {
      model: config.model,
      messages: messages,
      temperature: 0.5 
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