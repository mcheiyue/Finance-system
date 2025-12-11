import React, { createContext, useContext, useState, useLayoutEffect } from 'react';
import axios from 'axios';
import { App } from 'antd'; 
import { useNavigate, useLocation } from 'react-router-dom';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  
  
  
  const { message } = App.useApp();

  
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    const roles = localStorage.getItem('roles');
    
    if (token) {
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      return { token, username, roles: JSON.parse(roles || '[]') };
    }
    return null;
  });

  
  useLayoutEffect(() => {
    const reqInterceptor = axios.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    const resInterceptor = axios.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response && error.response.status === 401) {
          if (window.location.pathname !== '/login' && localStorage.getItem('token')) {
            console.warn('Token 失效，强制登出');
            logout(false); 
            message.error('登录已过期，请重新登录');
          }
        }
        return Promise.reject(error);
      }
    );

    return () => {
      axios.interceptors.request.eject(reqInterceptor);
      axios.interceptors.response.eject(resInterceptor);
    };
  }, []);

  
  const login = async (username, password) => {
    try {
      delete axios.defaults.headers.common['Authorization'];
      
      const res = await axios.post('http://localhost:8080/api/auth/login', { username, password });
      const { token, username: name, roles } = res.data;
      
      localStorage.setItem('token', token);
      localStorage.setItem('username', name);
      localStorage.setItem('roles', JSON.stringify(roles));
      
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      
      setUser({ token, username: name, roles });
      message.success('登录成功！');
      navigate('/'); 
      return true;
    } catch (err) {
      message.error(err.response?.data?.message || '登录失败，请检查账号密码');
      return false;
    }
  };

  
  const register = async (username, email, password) => {
    try {
      await axios.post('http://localhost:8080/api/auth/register', { username, email, password });
      message.success('注册成功，请登录');
      navigate('/login');
      return true;
    } catch (err) {
      message.error(err.response?.data || '注册失败');
      return false;
    }
  };

  
  const logout = (showMessage = true) => {
    localStorage.clear(); 
    delete axios.defaults.headers.common['Authorization'];
    setUser(null);
    if (showMessage) message.info('已退出登录');
    navigate('/login');
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
};