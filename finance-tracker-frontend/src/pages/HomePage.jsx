import React, { useState, useEffect } from 'react';
import {
  Button, Table, Modal, Form, InputNumber, Select, Radio, Input,
  message, Spin, Card, Tag, Tooltip, Popconfirm, Row, Col, Space,
  DatePicker, theme, Typography, Avatar, App, Grid, Pagination
} from 'antd';
import {
  DeleteOutlined, SearchOutlined, ReloadOutlined, DownloadOutlined, ArrowRightOutlined,
  AppstoreOutlined, CoffeeOutlined, CarOutlined, ShoppingOutlined,
  SkinOutlined, HomeOutlined, MedicineBoxOutlined, ReadOutlined,
  BankOutlined, RestOutlined, GiftOutlined, PlusOutlined,
  QuestionCircleOutlined, ExclamationCircleOutlined, DollarOutlined, EditOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { EXPENSE_CATEGORIES, INCOME_CATEGORIES } from '../constants';

const { Option } = Select;
const { Text } = Typography;

const CATEGORY_ICONS = {
  '餐饮': <CoffeeOutlined />, '交通': <CarOutlined />, '购物': <ShoppingOutlined />,
  '娱乐': <SkinOutlined />, '住房': <HomeOutlined />, '医疗': <MedicineBoxOutlined />,
  '教育': <ReadOutlined />, '其他': <AppstoreOutlined />, '工资': <BankOutlined />,
  '兼职': <RestOutlined />, '投资': <DollarOutlined />, '红包': <GiftOutlined />
};

function HomePage() {
  const { modal } = App.useApp();
  const [allData, setAllData] = useState([]);
  const [displayData, setDisplayData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [currentType, setCurrentType] = useState('expense');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchText, setSearchText] = useState('');
  const [filterType, setFilterType] = useState('all');
  const [startDate, setStartDate] = useState(null);
  const [endDate, setEndDate] = useState(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [editingId, setEditingId] = useState(null);

  const [form] = Form.useForm();
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();

  const loadTransactions = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/transactions');
      setAllData(response.data);
      setDisplayData(response.data);
      setSelectedRowKeys([]);
    } catch (error) { message.error(error.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { loadTransactions(); }, []);

  useEffect(() => {
    let result = [...allData];
    if (filterType !== 'all') result = result.filter(item => item.type === filterType);
    if (searchText.trim()) {
      const key = searchText.toLowerCase();
      result = result.filter(item => (item.category && item.category.includes(key)) || (item.description && item.description.toLowerCase().includes(key)));
    }
    if (startDate) result = result.filter(item => new Date(item.timestamp).getTime() >= startDate.startOf('day').valueOf());
    if (endDate) result = result.filter(item => new Date(item.timestamp).getTime() <= endDate.endOf('day').valueOf());
    setDisplayData(result);
    if (searchText || filterType !== 'all' || startDate || endDate) setCurrentPage(1);
  }, [allData, searchText, filterType, startDate, endDate]);

  const handleResetSearch = () => {
    setSearchText(''); setFilterType('all'); setStartDate(null); setEndDate(null);
    setDisplayData(allData); message.success('筛选已重置');
  };

  const handleDelete = async (id) => {
    try { await axios.delete(`/api/transactions/${id}`); message.success('删除成功'); loadTransactions(); } catch (error) { message.error('删除失败'); }
  };

  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) return;
    modal.confirm({
      title: `确认删除 ${selectedRowKeys.length} 条记录?`,
      icon: <ExclamationCircleOutlined />,
      okText: '删除', okType: 'danger', cancelText: '取消',
      onOk: async () => {
        try { await Promise.all(selectedRowKeys.map(id => axios.delete(`/api/transactions/${id}`))); message.success('批量删除成功'); loadTransactions(); } catch (e) { message.error('删除失败'); }
      },
    });
  };

  const handleExport = () => {
    let data = selectedRowKeys.length > 0 ? displayData.filter(i => selectedRowKeys.includes(i.id)) : displayData;
    if (!data.length) return message.warning('无数据可导出');
    const timestamps = data.map(item => new Date(item.timestamp).getTime());
    const minStr = dayjs(Math.min(...timestamps)).format('YYYYMMDD');
    const maxStr = dayjs(Math.max(...timestamps)).format('YYYYMMDD');
    const dateLabel = minStr === maxStr ? minStr : `${minStr}-${maxStr}`;
    const headers = ['类型,分类,金额,备注,时间'];
    const rows = data.map(i => {
      const desc = i.description ? `"${i.description.replace(/"/g, '""')}"` : '""';
      return `${i.type === 'income' ? '收入' : '支出'},${i.category},${i.amount},${desc},${dayjs(i.timestamp).format('YYYY-MM-DD HH:mm:ss')}`;
    });
    const blob = new Blob(['\uFEFF' + [headers, ...rows].join('\n')], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `账单_${dateLabel}.csv`;
    link.click();
  };

  const handleFinish = async (values) => {
    try {
      const payload = { ...values, timestamp: values.timestamp ? values.timestamp.toISOString() : new Date().toISOString() };

      if (editingId) {
        await axios.put(`/api/transactions/${editingId}`, payload);
        message.success('更新成功');
      } else {
        await axios.post('/api/transactions', payload);
        message.success('保存成功');
      }

      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      loadTransactions();
    } catch (e) {
      message.error(e.message);
    }
  };

  const showModal = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ timestamp: dayjs() });
    setCurrentType('expense');
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingId(record.id);
    setCurrentType(record.type);

    form.setFieldsValue({
      type: record.type,
      category: record.category,
      amount: record.amount,
      description: record.description,
      timestamp: dayjs(record.timestamp),
    });

    setModalVisible(true);
  };

  const columns = [
    {
      title: '收支类型', dataIndex: 'type', width: 100, align: 'center',
      render: (type) => (
        <Tag color={type === 'income' ? 'success' : 'error'} style={{ borderRadius: 4, background: 'transparent', borderColor: type === 'income' ? token.colorSuccess : token.colorError, color: type === 'income' ? token.colorSuccess : token.colorError }}>
          {type === 'income' ? '收入' : '支出'}
        </Tag>
      ),
    },
    {
      title: '消费分类', dataIndex: 'category', width: 140, align: 'left',
      render: (text) => {
        const Icon = CATEGORY_ICONS[text] || <AppstoreOutlined />;
        return <Space><Avatar size={22} icon={Icon} style={{ background: 'transparent', color: token.colorText, border: `1px solid ${token.colorBorder}` }} /><Text strong>{text}</Text></Space>;
      }
    },
    {
      title: '金额', dataIndex: 'amount', align: 'right', width: 150, sorter: (a, b) => a.amount - b.amount,
      render: (amount, record) => {
        const isIncome = record.type === 'income';
        return <span className="font-mono" style={{ color: isIncome ? token.colorSuccess : token.colorError, fontWeight: 600, fontSize: 15 }}>{isIncome ? '+' : '-'} {Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</span>;
      },
    },
    {
      title: '预算', key: 'budget', width: 100, align: 'center',
      render: (_, record) => {
        if (record.type !== 'expense') return <span style={{ color: token.colorTextSecondary }}>-</span>;
        const budgets = JSON.parse(localStorage.getItem('finance_budgets') || '{}');
        const limit = budgets[record.category];
        if (!limit) return <Tooltip title="未设置"><QuestionCircleOutlined style={{ color: token.colorTextSecondary }} /></Tooltip>;
        return record.amount > limit ? <Tag color="error" bordered={false}>超支</Tag> : <Tag color="success" variant={false}>正常</Tag>;
      }
    },
    { title: '备注', dataIndex: 'description', ellipsis: true, render: t => <span style={{ color: token.colorTextSecondary }}>{t || '-'}</span> },
    { title: '日期', dataIndex: 'timestamp', width: 160, align: 'center', render: t => <span style={{ color: token.colorTextSecondary }}>{dayjs(t).format('YYYY-MM-DD HH:mm')}</span> },
    {
      title: '操作', key: 'action', width: 120, align: 'center',
      render: (_, record) => (
        <Space>
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          />
          <Popconfirm title="删除?" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const currentMobileData = displayData.slice(startIndex, endIndex);

  return (
    <div style={{ margin: '0 auto', marginTop: 24 }}>
      <Card variant="borderless" style={{ marginBottom: 24 }} styles={{ body: { padding: '20px 24px' } }}>
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} sm={12} md={6}><Input placeholder="搜索..." prefix={<SearchOutlined style={{ color: token.colorTextSecondary }} />} value={searchText} onChange={e => setSearchText(e.target.value)} allowClear /></Col>
          <Col xs={24} sm={12} md={5}><Select defaultValue="all" style={{ width: '100%' }} value={filterType} onChange={setFilterType} options={[{ value: 'all', label: '全部' }, { value: 'income', label: '收入' }, { value: 'expense', label: '支出' }]} /></Col>
          <Col xs={24} sm={12} md={9}><div style={{ display: 'flex', gap: 8 }}><DatePicker placeholder="开始" value={startDate} onChange={setStartDate} style={{ flex: 1 }} disabledDate={c => endDate ? c.isAfter(endDate, 'day') : false} /><ArrowRightOutlined style={{ color: token.colorTextSecondary }} /><DatePicker placeholder="结束" value={endDate} onChange={setEndDate} style={{ flex: 1 }} disabledDate={c => startDate ? c.isBefore(startDate, 'day') : false} /></div></Col>
          <Col xs={24} sm={12} md={4} style={{ textAlign: 'right' }}><Space><Button icon={<ReloadOutlined />} onClick={handleResetSearch}>重置</Button><Button icon={<DownloadOutlined />} onClick={handleExport}>导出</Button></Space></Col>
        </Row>
      </Card>

      <Card variant="borderless" title={<Space><AppstoreOutlined /><span>账单明细</span></Space>} extra={<Space>{selectedRowKeys.length > 0 && (<Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>删除</Button>)}<Button type="primary" icon={<PlusOutlined />} onClick={showModal}>记一笔</Button></Space>}>
        <Spin spinning={loading}>
          {!screens.md ? (
            <div>
              {currentMobileData.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {currentMobileData.map((item) => {
                    const isIncome = item.type === 'income';
                    const Icon = CATEGORY_ICONS[item.category] || <AppstoreOutlined />;

                    return (
                      <Card
                        key={item.id}
                        size="small"
                        style={{ width: '100%', borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}
                        styles={{ body: { padding: '12px' } }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                          <Space>
                            <Avatar
                              size={32}
                              icon={Icon}
                              style={{ backgroundColor: token.colorBgLayout, color: token.colorText, border: `1px solid ${token.colorBorder}` }}
                            />
                            <Text strong style={{ fontSize: 16 }}>{item.category}</Text>
                            {item.type === 'expense' && (
                              (() => {
                                const budgets = JSON.parse(localStorage.getItem('finance_budgets') || '{}');
                                const limit = budgets[item.category];
                                return (limit && item.amount > limit) ? <Tag color="error" style={{ marginRight: 0 }}>超支</Tag> : null;
                              })()
                            )}
                          </Space>
                          <span className="font-mono" style={{ color: isIncome ? token.colorSuccess : token.colorError, fontWeight: 'bold', fontSize: 18 }}>
                            {isIncome ? '+' : '-'} {Number(item.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                          </span>
                        </div>

                        {item.description && (
                          <div style={{ marginBottom: 8, color: token.colorTextSecondary, fontSize: 13, background: token.colorBgLayout, padding: '4px 8px', borderRadius: 4 }}>
                            {item.description}
                          </div>
                        )}

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {dayjs(item.timestamp).format('YYYY-MM-DD HH:mm')}
                          </Text>
                          <Space>
                            <Button size="small" type="text" icon={<EditOutlined />} onClick={() => handleEdit(item)}>编辑</Button>
                            <Popconfirm title="确认删除此记录?" onConfirm={() => handleDelete(item.id)} okText="删除" cancelText="取消">
                              <Button size="small" type="text" danger icon={<DeleteOutlined />}>删除</Button>
                            </Popconfirm>
                          </Space>
                        </div>
                      </Card>
                    );
                  })}

                  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', marginTop: 16, width: '100%' }}>
                    <Pagination
                      simple
                      current={currentPage}
                      pageSize={pageSize}
                      total={displayData.length}
                      onChange={(page) => setCurrentPage(page)}
                    />
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '20px', color: token.colorTextSecondary }}>暂无数据</div>
              )}
            </div>
          ) : (
            <Table
              rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
              dataSource={displayData}
              columns={columns}
              rowKey="id"
              size="middle"
              pagination={{
                current: currentPage,
                pageSize: pageSize,
                total: displayData.length,
                pageSizeOptions: ['10', '20', '50', '100'],
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条 / 共 ${total} 条`,
                placement: ['bottomCenter'],
                onChange: (p, s) => {
                  setCurrentPage(p);
                  setPageSize(s);
                }
              }}
            />
          )}
        </Spin>
      </Card>

      <Modal title={editingId ? "编辑记录" : "新增记录"} open={modalVisible} onCancel={() => setModalVisible(false)} footer={null} width={500} zIndex={1050}>
        <Form form={form} layout="vertical" onFinish={handleFinish} initialValues={{ type: 'expense' }} style={{ marginTop: 20 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="type" label="类型" rules={[{ required: true }]}>
                <Radio.Group onChange={e => { setCurrentType(e.target.value); form.setFieldsValue({ category: undefined }) }} value={currentType} buttonStyle="solid" style={{ width: '100%' }}>
                  <Radio.Button value="expense" style={{ width: '50%', textAlign: 'center' }}>支出</Radio.Button>
                  <Radio.Button value="income" style={{ width: '50%', textAlign: 'center' }}>收入</Radio.Button>
                </Radio.Group>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="category" label="分类" rules={[{ required: true }]}>
                <Select placeholder="请选择" options={(currentType === 'expense' ? EXPENSE_CATEGORIES : INCOME_CATEGORIES).map(c => ({ label: c, value: c }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="amount" label="金额" rules={[{ required: true }]}>
            <InputNumber prefix="¥" style={{ width: '100%' }} size="large" min={0.01} precision={2} />
          </Form.Item>
          <Form.Item name="description" label="备注"><Input.TextArea rows={2} showCount maxLength={50} /></Form.Item>
          <Form.Item name="timestamp" label="日期" rules={[{ required: true }]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" block size="large" style={{ marginTop: 10 }}>保存</Button></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default HomePage;