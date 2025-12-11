import React, { useEffect, useState } from 'react';
import { Form, Input, Button, Card, Typography, theme, Spin, FloatButton } from 'antd';
import { UserOutlined, LockOutlined, DollarOutlined, SunOutlined, MoonOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../AuthContext';
import { useTheme } from '../ThemeContext'; 

const { Title, Text } = Typography;

export default function LoginPage() {
  const { login } = useAuth();
  const { token } = theme.useToken();
  const { isDarkMode, changeTheme } = useTheme(); 
  const [hitokoto, setHitokoto] = useState(null);

  useEffect(() => {
    const fetchHitokoto = () => {
      axios.get('https://v1.hitokoto.cn/?c=d&c=i&c=k')
        .then(res => setHitokoto(res.data))
        .catch(err => console.error(err));
    };
    fetchHitokoto();
    const interval = setInterval(fetchHitokoto, 5000);
    return () => clearInterval(interval);
  }, []);

  const onFinish = (values) => {
    login(values.username, values.password);
  };
  const toggleTheme = () => {
    changeTheme(isDarkMode ? 'light' : 'dark');
  };

  return (
    <div className="auth-container">
      {/* 👉 右上角主题切换按钮 */}
      <div style={{ position: 'absolute', top: 24, right: 24, zIndex: 20 }}>
        <Button 
          shape="circle" 
          size="large"
          icon={isDarkMode ? <MoonOutlined /> : <SunOutlined />} 
          onClick={toggleTheme}
          style={{ 
            backgroundColor: 'transparent', 
            borderColor: token.colorBorder, 
            color: token.colorText 
          }}
        />
      </div>

      <div className="auth-card-container">
        <Card className="auth-card" variant={false}>
          <div style={{ textAlign: 'center', marginBottom: 32 }}>
            <div className="auth-icon-box">
              <DollarOutlined style={{ fontSize: 32 }} />
            </div>
            <Title level={3} style={{ margin: '0 0 8px' }}>欢迎回来</Title>
            <Text type="secondary">登录个人理财记账系统</Text>
          </div>

          <Form name="login" initialValues={{ remember: true }} onFinish={onFinish} size="large">
            <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined style={{ opacity: 0.5 }} />} placeholder="用户名" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined style={{ opacity: 0.5 }} />} placeholder="密码" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 16 }}>
              <Button type="primary" htmlType="submit" block style={{ height: 45, fontWeight: 600 }}>
                立即登录
              </Button>
            </Form.Item>
            <div style={{ textAlign: 'center' }}>
              <span style={{ color: token.colorTextSecondary }}>还没有账号？</span> 
              <Link to="/register" style={{ fontWeight: 500, color: token.colorText }}>立即注册</Link>
            </div>
          </Form>
        </Card>
      </div>

      <div className="hitokoto-container" style={{ color: token.colorTextSecondary }}>
        {hitokoto ? (
          <div key={hitokoto.uuid} className="hitokoto-content">
            <div className="hitokoto-text">『 {hitokoto.hitokoto} 』</div>
            <div className="hitokoto-from">—— {hitokoto.from_who || ''} {hitokoto.from}</div>
          </div>
        ) : <Spin size="small" />}
      </div>
    </div>
  );
}