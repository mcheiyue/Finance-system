import request from '../utils/request';

export const getMonthlyReports = () => {
  return request.get('/reports/monthly');
};

export const getMonthlyReport = (month) => {
  return request.get(`/reports/monthly/${month}`);
};
