import request from '../utils/request';

export const getAccounts = (type) => {
  return request.get('/accounts', { params: type ? { type } : {} });
};

export const getAccountById = (id) => {
  return request.get(`/accounts/${id}`);
};

export const createAccount = (data) => {
  return request.post('/accounts', data);
};
