import axios from 'axios';
import { message } from 'antd';

const baseURL = import.meta.env.VITE_API_URL 
    ? `${import.meta.env.VITE_API_URL}/api` 
    : '/api';

const service = axios.create({
    baseURL: baseURL, 
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
                handleLoginExpired();
            }

            return Promise.reject(new Error(res.message || 'Error'));
        } else {
            return res.data;
        }
    },
    (error) => {
        const status = error.response?.status;
        const msg = error.response?.data?.message || '网络请求失败';

        if (status === 401) {
            message.error('登录已过期，请重新登录');
            handleLoginExpired();
        } else {
            message.error(msg);
        }

        return Promise.reject(error);
    }
);

function handleLoginExpired() {
    localStorage.removeItem('token');
}

export default service;