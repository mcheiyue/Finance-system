import React, { useState, useEffect } from 'react';
import { 
  Card, Form, Input, Button, Descriptions, Tag, message, Row, Col, Spin, theme 
} from 'antd';
import { 
  UserOutlined, LockOutlined, MailOutlined, SafetyCertificateOutlined, CheckCircleOutlined 
} from '@ant-design/icons';
import axios from 'axios';

export default function ProfilePage() {
  const [loading, setLoading] = useState(false);
  const [userInfo, setUserInfo] = useState(null);
  const { token } = theme.useToken();
  const [form] = Form.useForm();
  const loadProfile = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/users/me');
      setUserInfo(res.data);
    } catch (err) {
      message.error('加载用户信息失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProfile();
  }, []);
  const handlePasswordChange = async (values) => {
    try {
      await axios.post('/api/users/password', values);
      message.success('密码修改成功！下次登录请使用新密码。');
      form.resetFields();
    } catch (err) {
      message.error(err.response?.data || '修改失败');
    }
  };

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <Row gutter={[24, 24]}>
        
        {/* 左侧：个人资料卡片 */}
        <Col xs={24} md={10}>
          <Card 
            title={<span><UserOutlined /> 个人资料</span>}
            variant={false}
            style={{ height: '100%', borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
          >
            <Spin spinning={loading}>
              {userInfo && (
                <div style={{ textAlign: 'center', padding: '20px 0' }}>
                  <div style={{ 
                    width: 80, height: 80, background: token.colorPrimaryBg, 
                    borderRadius: '50%', margin: '0 auto 20px', 
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 40, color: token.colorPrimary 
                  }}>
                    {userInfo.username[0].toUpperCase()}
                  </div>
                  
                  <Descriptions column={1} bordered size="small">
                    <Descriptions.Item label="用户 ID">{userInfo.id}</Descriptions.Item>
                    <Descriptions.Item label="用户名">{userInfo.username}</Descriptions.Item>
                    <Descriptions.Item label="绑定邮箱">{userInfo.email}</Descriptions.Item>
                    <Descriptions.Item label="当前角色">
                      {userInfo.roles.map(r => (
                        <Tag key={r} color={r === 'ROLE_ADMIN' ? 'red' : 'blue'}>
                          {r === 'ROLE_ADMIN' ? '管理员' : '普通用户'}
                        </Tag>
                      ))}
                    </Descriptions.Item>
                  </Descriptions>
                </div>
              )}
            </Spin>
          </Card>
        </Col>

        {/* 右侧：安全设置卡片 */}
        <Col xs={24} md={14}>
          <Card 
            title={<span><SafetyCertificateOutlined /> 安全设置</span>}
            variant={false}
            style={{ height: '100%', borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
          >
            <Form 
              form={form} 
              layout="vertical" 
              onFinish={handlePasswordChange}
              style={{ maxWidth: 400, margin: '0 auto', padding: '20px 0' }}
            >
              <Form.Item
                name="oldPassword"
                label="当前密码"
                rules={[{ required: true, message: '请输入当前密码' }]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder="请输入旧密码" />
              </Form.Item>

              <Form.Item
                name="newPassword"
                label="新密码"
                rules={[
                  { required: true, message: '请输入新密码' },
                  { min: 6, message: '密码长度不能少于6位' }
                ]}
              >
                <Input.Password prefix={<CheckCircleOutlined />} placeholder="设置新密码" />
              </Form.Item>

              <Form.Item
                name="confirm"
                label="确认新密码"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: '请确认新密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('newPassword') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('两次输入的密码不一致!'));
                    },
                  }),
                ]}
              >
                <Input.Password prefix={<CheckCircleOutlined />} placeholder="再次输入新密码" />
              </Form.Item>

              <Form.Item>
                <Button type="primary" htmlType="submit" block size="large">
                  确认修改
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>
      </Row>
    </div>
  );
}