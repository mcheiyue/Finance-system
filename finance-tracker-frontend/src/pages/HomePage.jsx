import React, { useState, useEffect } from 'react';
import {
  Button, Table, Modal, Form, InputNumber, Select, Radio, Input,
  message, Spin, Card, Tag, Tooltip, Popconfirm, Row, Col, Space,
  DatePicker, theme, Typography, Avatar, App, Grid, Pagination, Drawer, Alert
} from 'antd';
import {
  DeleteOutlined, SearchOutlined, ReloadOutlined, DownloadOutlined, ArrowRightOutlined,
  AppstoreOutlined, CoffeeOutlined, CarOutlined, ShoppingOutlined,
  SkinOutlined, HomeOutlined, MedicineBoxOutlined, ReadOutlined,
  BankOutlined, RestOutlined, GiftOutlined, PlusOutlined,
  QuestionCircleOutlined, ExclamationCircleOutlined, DollarOutlined, EditOutlined,
  RobotOutlined, SettingOutlined, SendOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import ReactMarkdown from 'react-markdown';
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
  const { modal, message: msgApi } = App.useApp();
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

  const [aiDrawerVisible, setAiDrawerVisible] = useState(false); // 控制 AI 抽屉显示
  const [chatInput, setChatInput] = useState(''); // 聊天输入框内容
  const [messages, setMessages] = useState([
    { role: 'ai', content: '您好！我是您的 AI 财务助手。您可以尝试对我描述一笔消费，或者让我分析当前的账单。' }
  ]);

  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [aiConfig, setAiConfig] = useState(() => {
    // 初始化时从 localStorage 读取，如果没有则使用默认值
    const saved = localStorage.getItem('ai_chat_config');
    return saved ? JSON.parse(saved) : {
      apiKey: '',
      baseUrl: 'https://api.openai.com/v1',
      model: 'gpt-3.5-turbo'
    };
  });

  const callAiApi = async (userMessage) => {
    if (!aiConfig.apiKey) {
      message.error('请先配置 API Key');
      return;
    }

    const aiInstance = axios.create();

    // 1. 数据脱敏
    const sanitizedData = displayData.map(item => ({
      type: item.type === 'income' ? '收入' : '支出',
      category: item.category,
      amount: item.amount,
      description: item.description,
      date: dayjs(item.timestamp).format('YYYY-MM-DD')
    }));

    const systemPrompt = `
你是一个毒舌财务助手。用户数据：${JSON.stringify(sanitizedData.slice(0, 30))}

# 记账指令
1. 仅针对用户【最新发送】的一条消息提取动作。
2. 如果用户提到的内容已在历史记录中记录过，请勿重复生成 ACTION。
3. 必须严格按以下格式输出，不要有空格：
[ACTION]{"type":"expense","amount":0,"category":"分类","description":"备注"}[/ACTION]

# 约束
1. 必须使用系统支持的分类：
   支出：${EXPENSE_CATEGORIES.join(',')}
   收入：${INCOME_CATEGORIES.join(',')}
2. 请根据描述自动匹配最准确的分类。
`;

    try {
      // 修改 callAiApi 内部的消息构造逻辑
      const formattedMessages = [
        { role: 'system', content: systemPrompt },
        ...messages.slice(-5).map(m => ({
          role: m.role === 'ai' || m.role === 'assistant' ? 'assistant' : 'user',
          // 关键：发送前剔除历史消息中的 [ACTION] 内容，只留纯文本
          content: String(m.content).replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '').trim()
        })),
        { role: 'user', content: userMessage }
      ];

      const response = await aiInstance.post(`${aiConfig.baseUrl}/chat/completions`, {
        model: aiConfig.model,
        messages: formattedMessages, // 使用格式化后的消息
        temperature: 0.7
      }, {
        headers: {
          'Authorization': `Bearer ${aiConfig.apiKey.trim()}`,
          'Content-Type': 'application/json'
        }
      });

      return response.data.choices[0].message.content;
    } catch (error) {
      // 3. 增强错误日志捕获，防止 undefined 报错
      const errorDetail = error.response?.data || error.message;
      console.error('AI API 详细错误:', errorDetail);

      // 这里的错误提取逻辑要更健壮
      const errorMsg = error.response?.data?.error?.message
        || error.response?.data?.message
        || '请求参数错误(400)，请检查模型名称和格式';

      throw new Error(errorMsg);
    }
  };

  const [configForm] = Form.useForm();

  const [form] = Form.useForm();
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();

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

  const handleSendMessage = async () => {
    if (!chatInput.trim()) return;
    const userMsg = { role: 'user', content: chatInput };
    setMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setLoading(true);

    try {
      const aiResponse = await callAiApi(chatInput);

      // 1. 使用更包容的正则：允许标签前后有空格
      const actionRegex = /\[ACTION\]\s*(\{[\s\S]*?\})\s*\[\/ACTION\]/;
      const match = aiResponse.match(actionRegex);

      if (match) {
        try {
          const actionData = JSON.parse(match[1]);
          // 2. 无论解析是否成功，显示给用户的消息必须剔除 [ACTION] 源码
          const cleanText = aiResponse.replace(actionRegex, '').trim();
          setMessages(prev => [...prev, { role: 'assistant', content: cleanText || '好的，请确认账单详情。' }]);

          // 3. 动作联动
          form.setFieldsValue({
            ...actionData,
            timestamp: dayjs()
          });
          setCurrentType(actionData.type); // 必须手动触发 type 状态更新以切换 Radio
          setModalVisible(true);
          msgApi.success('AI 已预填账单');
        } catch (e) {
          // 如果 JSON 解析报错，清理标签后显示文本
          setMessages(prev => [...prev, { role: 'assistant', content: aiResponse.replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '') }]);
        }
      } else {
        setMessages(prev => [...prev, { role: 'assistant', content: aiResponse }]);
      }
    } catch (e) {
      msgApi.error(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveConfig = (values) => {
    setAiConfig(values);
    localStorage.setItem('ai_chat_config', JSON.stringify(values));
    message.success('AI 配置已本地保存');
    setConfigModalVisible(false);
  };


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

      <Card variant="borderless" title={<Space><AppstoreOutlined /><span>账单明细</span></Space>}
        extra={
          <Space>
            {selectedRowKeys.length > 0 && (
              <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>删除</Button>
            )}
            <Button
              icon={<RobotOutlined />}
              onClick={() => setAiDrawerVisible(true)}
              style={{ borderColor: token.colorPrimary, color: token.colorPrimary }}
            >
              AI 助手
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showModal}>记一笔</Button>
          </Space>
        }>
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

      <Modal
        title="AI 助手配置 (仅本地存储)"
        open={configModalVisible}
        onCancel={() => setConfigModalVisible(false)}
        onOk={() => configForm.submit()}
        okText="保存"
        cancelText="取消"
        zIndex={1200} // 确保比 Drawer (1100) 更高
      >
        <Form
          form={configForm}
          layout="vertical"
          initialValues={aiConfig}
          onFinish={handleSaveConfig}
        >
          <Form.Item
            label="API Key"
            name="apiKey"
            rules={[{ required: true, message: '请输入 API Key' }]}
            tooltip="密钥仅存在您的浏览器缓存中"
          >
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item
            label="Base URL"
            name="baseUrl"
            rules={[{ required: true }]}
          >
            <Input placeholder="例如 https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item
            label="模型名称 (Model)"
            name="model"
            rules={[{ required: true }]}
          >
            <Input placeholder="例如 gpt-3.5-turbo 或 deepseek-chat" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span><RobotOutlined /> AI 财务助手</span>
            <Space>
              <Button
                type="text"
                icon={<SettingOutlined />}
                onClick={() => {
                  configForm.setFieldsValue(aiConfig);
                  setConfigModalVisible(true);
                }}
              />
              {/* 移动端增加显式的关闭文字或按钮（可选） */}
              {!screens.md && (
                <Button type="text" onClick={() => setAiDrawerVisible(false)}>关闭</Button>
              )}
            </Space>
          </div>
        }
        placement="right"
        onClose={() => setAiDrawerVisible(false)}
        open={aiDrawerVisible}
        // 关键修复 1：设置比导航栏更高的层级，确保不被遮挡
        zIndex={1100}
        // 关键修复 2：响应式宽度切换
        width={screens.md ? 400 : '100%'}
        // 移除默认关闭按钮（因为我们在 title 里自定义了，或者保留默认）
        closable={screens.md}
        styles={{
          body: {
            display: 'flex',
            flexDirection: 'column',
            padding: 0,
            height: '100%' // 确保在手机端撑开
          }
        }}
      >
        {!aiConfig.apiKey && (
          <div style={{ marginBottom: 20 }}>
            <Alert
              message="未检测到配置"
              description="请点击右上角齿轮图标配置您的 API Key 以启用 AI 功能。"
              type="warning"
              showIcon
            />
          </div>
        )}
        {/* 消息展示区：增加对触摸滚动的支持 */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '20px',
          // 使用主题 Token 确保背景色随深浅色模式切换
          background: token.colorBgLayout
        }}>
          {messages.map((msg, index) => {
            const isUser = msg.role === 'user';
            return (
              <div key={index} style={{
                marginBottom: '16px',
                textAlign: isUser ? 'right' : 'left'
              }}>
                <div style={{
                  display: 'inline-block',
                  padding: '10px 14px',
                  borderRadius: '12px',
                  maxWidth: '90%',
                  background: isUser ? '#141414' : token.colorBgElevated,
                  color: isUser ? '#ffffff' : token.colorText,
                  boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
                  border: isUser ? '1px solid #333' : '1px solid transparent'
                }}>
                  {isUser ? (
                    msg.content
                  ) : (
                    <div className="markdown-content">
                      <ReactMarkdown>{msg.content}</ReactMarkdown>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* 输入区：在移动端增加底部安全区间距 */}
        <div style={{
          padding: screens.md ? '16px' : '16px 16px 32px 16px',
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          background: token.colorBgContainer
        }}>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder="问问 AI，例如：我最近花钱多吗？"
              value={chatInput}
              onChange={e => setChatInput(e.target.value)}
              onPressEnter={handleSendMessage} // 绑定回车
              disabled={loading}
            />
            <Button
              type="primary"
              icon={loading ? <Spin size="small" /> : <SendOutlined />}
              onClick={handleSendMessage} // 绑定点击
              disabled={loading}
            />
          </Space.Compact>
        </div>
      </Drawer>


    </div>
  );
}

export default HomePage;