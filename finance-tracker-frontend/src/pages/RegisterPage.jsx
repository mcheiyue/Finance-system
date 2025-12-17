import React from 'react';
import { Form, Input, Button, Card, Typography, theme } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, DollarOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';

const { Title, Text } = Typography;

export default function RegisterPage() {
  const { register } = useAuth();

  const onFinish = (values) => {
    register(values.username, values.email, values.password);
  };

  return (
    <div className="auth-container">
      <div className="auth-card-container">
        <Card className="auth-card" variant={false}>

          <div style={{ textAlign: 'center', marginBottom: 32 }}>
            <div className="auth-icon-box">
              <DollarOutlined style={{ fontSize: 32 }} />
            </div>

            <Title level={3} style={{ margin: '0 0 8px' }}>创建账号</Title>
            <Text type="secondary">开启您的财富管理之旅</Text>
          </div>

          <Form
            name="register"
            onFinish={onFinish}
            size="large"
            layout="vertical"
          >
            <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}><Input prefix={<UserOutlined style={{ opacity: 0.5 }} />} placeholder="设置用户名" /></Form.Item>
            <Form.Item name="email" rules={[{ required: true, type: 'email', message: '邮箱格式不正确' }]}><Input prefix={<MailOutlined style={{ opacity: 0.5 }} />} placeholder="example@mail.com" /></Form.Item>
            <Form.Item name="password" rules={[{ required: true, min: 6, message: '至少6位' }]}><Input.Password prefix={<LockOutlined style={{ opacity: 0.5 }} />} placeholder="设置密码" /></Form.Item>
            <Form.Item name="confirm" dependencies={['password']} rules={[{ required: true, message: '请确认密码' }, ({ getFieldValue }) => ({ validator(_, value) { if (!value || getFieldValue('password') === value) { return Promise.resolve(); } return Promise.reject(new Error('两次密码不一致!')); }, }),]}><Input.Password prefix={<LockOutlined style={{ opacity: 0.5 }} />} placeholder="确认密码" /></Form.Item>
            <Form.Item style={{ marginBottom: 16, marginTop: 24 }}><Button type="primary" htmlType="submit" block style={{ height: 45, fontWeight: 600, borderRadius: 8 }}>注册并登录</Button></Form.Item>
            <div style={{ textAlign: 'center' }}><span style={{ color: 'var(--ant-color-text-secondary)' }}>已有账号？</span> <Link to="/login" style={{ fontWeight: 500 }}>直接登录</Link></div>
          </Form>
        </Card>
      </div>
    </div>
  );
}