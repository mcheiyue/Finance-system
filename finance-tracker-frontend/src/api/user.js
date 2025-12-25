import request from '../utils/request';


export const getUserProfile = () => {
    return request.get('/users/me');
};

export const changePassword = (data) => {
    return request.post('/users/password', data);
};


export const getAllUsers = () => {
    return request.get('/admin/users');
};

export const getAllTransactionsAdmin = () => {
    return request.get('/admin/transactions/all');
};

export const deleteUser = (userId) => {
    return request.delete(`/admin/users/${userId}`);
};