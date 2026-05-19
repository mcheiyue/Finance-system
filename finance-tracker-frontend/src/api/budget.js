import request from '../utils/request';

export const getBudgets = () => request.get('/budgets');

export const getBudgetExecution = () => request.get('/budgets/execution');

export const createBudget = (data) => request.post('/budgets', data);

export const deleteBudget = (id) => request.delete(`/budgets/${id}`);
