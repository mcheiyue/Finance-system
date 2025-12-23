export const TRANSACTION_TYPES = [
  { value: 'income', label: '收入' },
  { value: 'expense', label: '支出' },
];

export const EXPENSE_CATEGORIES = [
  '餐饮', '交通', '购物', '娱乐', '住房', '医疗', '教育', '其他'
];

export const INCOME_CATEGORIES = [
  '工资', '兼职', '投资', '红包', '其他'
];

export const DEFAULT_AI_CONFIG = {
  // 请在此处填入内置的 Key，用户如果没有配置自己的 Key，将默认使用这个
  apiKey: 'sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 
  baseUrl: 'https://gcli2api-web.zeabur.app/v1', // 注意：通常 OpenAI 兼容接口需要 /v1 后缀
  model: 'gpt-3.5-turbo'
};