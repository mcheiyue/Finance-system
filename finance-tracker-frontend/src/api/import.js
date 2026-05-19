import request from '../utils/request';

export const previewCsv = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/csv/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const confirmCsvImport = (transactions) => {
  return request.post('/csv/confirm', { transactions });
};
