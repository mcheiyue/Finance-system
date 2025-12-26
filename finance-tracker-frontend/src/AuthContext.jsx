import React, { createContext, useState, useContext, useEffect } from 'react';
import axios from './utils/request';
import { message } from 'antd';
import { useNavigate } from 'react-router-dom';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      // 简单恢复会话，如果需要更严谨，可以请求后端 /api/users/me
      setUser({ name: 'User' }); 
    }
    setLoading(false);
  }, []);

  const login = async (username, password) => {
    try {
      const data = await axios.post('/auth/login', { username, password });

      if (data.token) {
        localStorage.setItem('token', data.token);
        setUser({
          username: data.username,
          roles: data.roles
        });
        message.success('登录成功');
        navigate('/');
      }
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  const register = async (username, email, password) => {
    try {
      await axios.post('/auth/register', { username, email, password });
      message.success('注册成功，请登录');
      navigate('/login');
    } catch (error) {
      console.error('Register failed:', error);
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
    navigate('/login');
    message.success('已退出登录');
  };

  // 核心修复：添加 isAuthenticated 属性
  // 通过判断 user 是否存在来决定是否已认证
  const value = {
    user,
    login,
    register,
    logout,
    loading,
    isAuthenticated: !!user, // <--- 加上这一句！!!user 把对象转为布尔值
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);