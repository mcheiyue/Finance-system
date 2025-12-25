import request from '../utils/request';

export const getTransactions = () => {
    return request.get('/transactions');
};

export const getRecentTransactions = (days = 7) => {
    return request.get('/transactions/stats/recent', { params: { days } });
};

export const createTransaction = (data) => {
    return request.post('/transactions', data);
};

export const updateTransaction = (id, data) => {
    return request.put(`/transactions/${id}`, data);
};

export const deleteTransaction = (id) => {
    return request.delete(`/transactions/${id}`);
};

export const getStatsByType = (days = 30) => {
    return request.get('/transactions/stats/type', { params: { days } });
};

export const getStatsByCategory = (type, days = 30) => {
    return request.get('/transactions/stats/category', {
        params: { type, days }
    });
};