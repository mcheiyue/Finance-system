import axios from 'axios';
import { message } from 'antd';

const service = axios.create({
    baseURL: '/api',
    timeout: 10000,
});

service.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

service.interceptors.response.use(
    (response) => {
        const res = response.data;

        if (res.code === undefined) {
            return res;
        }

        if (res.code !== 200) {
            message.error(res.message || '系统繁忙');

            if (res.code === 401) {
            }

            return Promise.reject(new Error(res.message || 'Error'));
        } else {
            return res.data;
        }
    },
    (error) => {
        const msg = error.response?.data?.message || '网络请求失败';
        message.error(msg);
        return Promise.reject(error);
    }
);

export default service;