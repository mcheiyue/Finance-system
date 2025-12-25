import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Tabs, Tag, message, Popconfirm, Spin, theme, Space } from 'antd';
import { UserOutlined, DeleteOutlined, SafetyCertificateOutlined, UnorderedListOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getAllUsers, getAllTransactionsAdmin, deleteUser } from '../api/user';

const { TabPane } = Tabs;

export default function AdminPage() {
  const [users, setUsers] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const { token } = theme.useToken();

  const loadUsers = async () => {
    setLoading(true);
    try {
            const data = await getAllUsers();
      setUsers(data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const loadAllTransactions = async () => {
    setLoading(true);
    try {
            const data = await getAllTransactionsAdmin();
      setTransactions(data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleDeleteUser = async (id) => {
    try {
            await deleteUser(id);
      message.success('用户及其数据已删除');
      loadUsers();
    } catch (err) { console.error(err); }
  };

  useEffect(() => { loadUsers(); }, []);

    const userColumns = [
    { title: 'ID', dataIndex: 'id', width: 220, ellipsis: true },
    { title: '用户名', dataIndex: 'username', render: (text) => <b>{text}</b> },
    { title: '邮箱', dataIndex: 'email' },
    { title: '角色', dataIndex: 'roles', render: (roles) => roles.map(role => (<Tag key={role} color={role === 'ROLE_ADMIN' ? 'geekblue' : 'default'}>{role === 'ROLE_ADMIN' ? '管理员' : '普通用户'}</Tag>)) },
    { title: '操作', key: 'action', render: (_, record) => { if (record.username === 'admin') return <Tag>不可操作</Tag>; return (<Popconfirm title="高危操作警告" description={`确定要删除用户 ${record.username} 吗？\n这将连带删除该用户的所有账单数据！`} onConfirm={() => handleDeleteUser(record.id)} okText="确认删除" okType="danger" cancelText="取消" icon={<ExclamationCircleOutlined style={{ color: 'red' }} />}><Button danger type="primary" size="small" icon={<DeleteOutlined />}>销号</Button></Popconfirm>); } }
  ];
  const transactionColumns = [
    { title: '用户ID', dataIndex: 'userId', width: 150, ellipsis: true, render: (text) => <Tag>{text}</Tag> },
    { title: '类型', dataIndex: 'type', width: 80, render: t => <span style={{ color: t === 'income' ? token.colorSuccess : token.colorError }}>{t === 'income' ? '收入' : '支出'}</span> },
    { title: '分类', dataIndex: 'category', width: 100 },
    { title: '金额', dataIndex: 'amount', align: 'right', render: val => `¥${Number(val).toFixed(2)}` },
    { title: '备注', dataIndex: 'description', ellipsis: true },
    { title: '日期', dataIndex: 'timestamp', width: 160, render: t => dayjs(t).format('YYYY-MM-DD HH:mm') },
  ];

  const tabItems = [
    { key: '1', label: <span><UserOutlined /> 用户管理</span>, children: <Table dataSource={users} columns={userColumns} rowKey="id" /> },
    { key: '2', label: <span><UnorderedListOutlined /> 全站审计</span>, children: <Table dataSource={transactions} columns={transactionColumns} rowKey="id" /> }
  ];

  return (
    <Card title={<Space><SafetyCertificateOutlined style={{ color: token.colorPrimary }} /> 管理员控制台</Space>} variant={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
      <Spin spinning={loading}>
        <Tabs defaultActiveKey="1" items={tabItems} onChange={(key) => { if (key === '2') loadAllTransactions(); }} />
      </Spin>
    </Card>
  );
}