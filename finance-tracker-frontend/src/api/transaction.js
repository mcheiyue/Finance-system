import request from '../utils/request';

export const getTransactions = (params = {}) => {
  return request.get('/transactions', { params: { size: 10000, ...params } });
};

export const getRecentTransactions = (days = 7) => {
  return request.get('/transactions/stats/recent', { params: { days } });
};

export const createTransaction = (data) => {
  return request.post('/transactions', data);
};

export const getTransactionById = (id) => {
  return request.get(`/transactions/${id}`);
};

export const reverseTransaction = (id) => {
  return request.post(`/transactions/${id}/reverse`);
};

export const getTransactionsPaginated = (params) => {
  return request.get('/transactions', { params });
};

// @deprecated - 后端已移除此端点，保留导出供 StatisticsPage 过渡使用
export const getStatsByType = (days = 30) => {
  return request.get('/transactions/stats/type', { params: { days } });
};

// @deprecated - 后端已移除此端点，保留导出供 StatisticsPage 过渡使用
export const getStatsByCategory = (type, days = 30) => {
  return request.get('/transactions/stats/category', {
    params: { type, days }
  });
};

// @deprecated - 后端已移除此端点，保留导出供可能的遗留代码过渡使用
export const updateTransaction = (id, data) => {
  return request.put(`/transactions/${id}`, data);
};

// @deprecated - 后端已移除此端点，保留导出供可能的遗留代码过渡使用
export const deleteTransaction = (id) => {
  return request.delete(`/transactions/${id}`);
};
