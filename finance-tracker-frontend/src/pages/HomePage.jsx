import React, { useState, useEffect, useCallback } from 'react';
import {
  Button, Table, Modal, Form, InputNumber, Select, Radio, Input,
  Spin, Card, Tag, Popconfirm, Row, Col, Space,
  DatePicker, theme, Typography, Avatar, App, Grid, Pagination
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, ArrowRightOutlined,
  AppstoreOutlined, CoffeeOutlined, CarOutlined, ShoppingOutlined,
  SkinOutlined, HomeOutlined, MedicineBoxOutlined, ReadOutlined,
  BankOutlined, RestOutlined, GiftOutlined, PlusOutlined,
  DollarOutlined, SwapOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getTransactions,
  createTransaction,
  reverseTransaction
} from '../api/transaction';
import { getAccounts } from '../api/account';

const { Text } = Typography;

const CATEGORY_ICONS = {
  '餐饮': <CoffeeOutlined />, '交通': <CarOutlined />, '购物': <ShoppingOutlined />,
  '娱乐': <SkinOutlined />, '住房': <HomeOutlined />, '医疗': <MedicineBoxOutlined />,
  '教育': <ReadOutlined />, '其他': <AppstoreOutlined />, '工资': <BankOutlined />,
  '兼职': <RestOutlined />, '投资': <DollarOutlined />, '红包': <GiftOutlined />
};

function HomePage() {
  const { message } = App.useApp();
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

  const [expenseAccounts, setExpenseAccounts] = useState([]);
  const [incomeAccounts, setIncomeAccounts] = useState([]);
  const [assetAccount, setAssetAccount] = useState(null);

  const [form] = Form.useForm();
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();

  const loadAccounts = useCallback(async () => {
    try {
      const [expense, income, all] = await Promise.all([
        getAccounts('EXPENSE'),
        getAccounts('INCOME'),
        getAccounts('ASSET')
      ]);
      setExpenseAccounts(expense || []);
      setIncomeAccounts(income || []);
      const defaultCash = (all || []).find(a => a.name === '默认现金');
      if (defaultCash) setAssetAccount(defaultCash);
    } catch (error) {
      console.error('加载账户失败:', error);
    }
  }, []);

  const loadTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getTransactions();
      setAllData(data?.content ?? []);
    } catch (error) {
      console.error('加载交易记录失败:', error);
      message.error('加载交易记录失败，请稍后重试');
      setAllData([]);
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    loadAccounts();
    loadTransactions();
  }, [loadAccounts, loadTransactions]);

  useEffect(() => {
    let result = [...allData];
    if (filterType !== 'all') {
      result = result.filter(item => {
        if (filterType === 'income') {
          return incomeAccounts.some(a => a.id === item.fromAccountId);
        }
        return expenseAccounts.some(a => a.id === item.toAccountId);
      });
    }
    if (searchText.trim()) {
      const key = searchText.toLowerCase();
      result = result.filter(item =>
        (item.description && item.description.toLowerCase().includes(key))
      );
    }
    if (startDate) result = result.filter(item => new Date(item.timestamp).getTime() >= startDate.startOf('day').valueOf());
    if (endDate) result = result.filter(item => new Date(item.timestamp).getTime() <= endDate.endOf('day').valueOf());

    setDisplayData(result);
    if (searchText || filterType !== 'all' || startDate || endDate) setCurrentPage(1);
  }, [allData, searchText, filterType, startDate, endDate, expenseAccounts, incomeAccounts]);

  const handleResetSearch = () => {
    setSearchText('');
    setFilterType('all');
    setStartDate(null);
    setEndDate(null);
    message.success('筛选已重置');
  };

  const handleReverse = async (id) => {
    try {
      await reverseTransaction(id);
      message.success('冲正成功');
      loadTransactions();
    } catch {
      message.error('冲正失败');
    }
  };

  const handleFinish = async (values) => {
    if (!assetAccount) {
      message.error('未找到默认现金账户');
      return;
    }

    try {
      const timestamp = values.timestamp ? values.timestamp.toISOString() : new Date().toISOString();
      let payload;

      if (currentType === 'expense') {
        payload = {
          fromAccountId: assetAccount.id,
          toAccountId: values.categoryAccountId,
          amount: values.amount,
          description: values.description,
          timestamp
        };
      } else {
        payload = {
          fromAccountId: values.categoryAccountId,
          toAccountId: assetAccount.id,
          amount: values.amount,
          description: values.description,
          timestamp
        };
      }

      const response = await createTransaction(payload);
      message.success('保存成功');

      if (response.anomalyWarnings && response.anomalyWarnings.length > 0) {
        message.warning(`异常警告: ${response.anomalyWarnings.join(', ')}`);
      }

      setModalVisible(false);
      form.resetFields();
      loadTransactions();
    } catch {
      message.error('保存失败');
    }
  };

  const showModal = () => {
    form.resetFields();
    form.setFieldsValue({ timestamp: dayjs() });
    setCurrentType('expense');
    setModalVisible(true);
  };

  const getAccountName = useCallback((id) => {
    const all = [...expenseAccounts, ...incomeAccounts, assetAccount].filter(Boolean);
    const account = all.find(a => a.id === id);
    return account ? account.name : id;
  }, [expenseAccounts, incomeAccounts, assetAccount]);

  const isIncomeTransaction = useCallback((record) => {
    return incomeAccounts.some(a => a.id === record.fromAccountId);
  }, [incomeAccounts]);

  const getCategoryName = useCallback((record) => {
    if (isIncomeTransaction(record)) {
      return getAccountName(record.fromAccountId);
    }
    return getAccountName(record.toAccountId);
  }, [isIncomeTransaction, getAccountName]);

  const columns = [
    {
      title: '消费分类', key: 'category', width: 140, align: 'left',
      render: (_, record) => {
        const name = getCategoryName(record);
        const Icon = CATEGORY_ICONS[name] || <AppstoreOutlined />;
        return (
          <Space>
            <Avatar size={22} icon={Icon} style={{
              background: 'transparent',
              color: token.colorText,
              border: `1px solid ${token.colorBorder}`
            }} />
            <Text strong>{name}</Text>
          </Space>
        );
      }
    },
    {
      title: '金额', dataIndex: 'amount', align: 'right', width: 150,
      sorter: (a, b) => a.amount - b.amount,
      render: (amount, record) => {
        const isIncome = isIncomeTransaction(record);
        return (
          <span className="font-mono" style={{
            color: isIncome ? token.colorSuccess : token.colorError,
            fontWeight: 600,
            fontSize: 15
          }}>
            {isIncome ? '+' : '-'} {Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
          </span>
        );
      },
    },
    {
      title: '备注', dataIndex: 'description', ellipsis: true,
      render: t => <span style={{ color: token.colorTextSecondary }}>{t || '-'}</span>
    },
    {
      title: '日期', dataIndex: 'timestamp', width: 160, align: 'center',
      render: t => <span style={{ color: token.colorTextSecondary }}>{dayjs(t).format('YYYY-MM-DD HH:mm')}</span>
    },
    {
      title: '操作', key: 'action', width: 120, align: 'center',
      render: (_, record) => (
        record.reversed ? (
          <Tag color="warning">已冲正</Tag>
        ) : (
          <Popconfirm title="确认冲正此笔记录?" onConfirm={() => handleReverse(record.id)}>
            <Button type="text" icon={<SwapOutlined />}>冲正</Button>
          </Popconfirm>
        )
      ),
    },
  ];

  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const currentMobileData = displayData.slice(startIndex, endIndex);

  const categoryOptions = (currentType === 'expense' ? expenseAccounts : incomeAccounts)
    .map(a => ({ label: a.name, value: a.id }));

  return (
    <div style={{ margin: '0 auto', marginTop: 24 }}>
      <Card variant="borderless" style={{ marginBottom: 24 }} styles={{ body: { padding: '20px 24px' } }}>
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} sm={12} md={6}>
            <Input
              placeholder="搜索..."
              prefix={<SearchOutlined style={{ color: token.colorTextSecondary }} />}
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              defaultValue="all"
              style={{ width: '100%' }}
              value={filterType}
              onChange={setFilterType}
              options={[
                { value: 'all', label: '全部' },
                { value: 'income', label: '收入' },
                { value: 'expense', label: '支出' }
              ]}
            />
          </Col>
          <Col xs={24} sm={12} md={9}>
            <div style={{ display: 'flex', gap: 8 }}>
              <DatePicker
                placeholder="开始"
                value={startDate}
                onChange={setStartDate}
                style={{ flex: 1 }}
                disabledDate={c => endDate ? c.isAfter(endDate, 'day') : false}
              />
              <ArrowRightOutlined style={{ color: token.colorTextSecondary }} />
              <DatePicker
                placeholder="结束"
                value={endDate}
                onChange={setEndDate}
                style={{ flex: 1 }}
                disabledDate={c => startDate ? c.isBefore(startDate, 'day') : false}
              />
            </div>
          </Col>
          <Col xs={24} sm={12} md={4} style={{ textAlign: 'right' }}>
            <Button icon={<ReloadOutlined />} onClick={handleResetSearch}>重置</Button>
          </Col>
        </Row>
      </Card>

      <Card
        variant="borderless"
        title={<Space><AppstoreOutlined /><span>账单明细</span></Space>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={showModal}>记一笔</Button>
        }
      >
        <Spin spinning={loading}>
          {!screens.md ? (
            <div>
              {currentMobileData.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {currentMobileData.map((item) => {
                    const isIncome = isIncomeTransaction(item);
                    const categoryName = getCategoryName(item);
                    const Icon = CATEGORY_ICONS[categoryName] || <AppstoreOutlined />;
                    return (
                      <Card key={item.id} size="small" style={{ width: '100%', borderRadius: 8 }} styles={{ body: { padding: '12px' } }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                          <Space>
                            <Avatar size={32} icon={Icon} style={{
                              backgroundColor: token.colorBgLayout,
                              color: token.colorText,
                              border: `1px solid ${token.colorBorder}`
                            }} />
                            <Text strong style={{ fontSize: 16 }}>{categoryName}</Text>
                          </Space>
                          <span className="font-mono" style={{
                            color: isIncome ? token.colorSuccess : token.colorError,
                            fontWeight: 'bold',
                            fontSize: 18
                          }}>
                            {isIncome ? '+' : '-'} {Number(item.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                          </span>
                        </div>
                        {item.description && (
                          <div style={{
                            marginBottom: 8,
                            color: token.colorTextSecondary,
                            fontSize: 13,
                            background: token.colorBgLayout,
                            padding: '4px 8px',
                            borderRadius: 4
                          }}>
                            {item.description}
                          </div>
                        )}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {dayjs(item.timestamp).format('YYYY-MM-DD HH:mm')}
                          </Text>
                          {item.reversed ? (
                            <Tag color="warning">已冲正</Tag>
                          ) : (
                            <Popconfirm title="确认冲正?" onConfirm={() => handleReverse(item.id)}>
                              <Button size="small" type="text" icon={<SwapOutlined />}>冲正</Button>
                            </Popconfirm>
                          )}
                        </div>
                      </Card>
                    );
                  })}
                  <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                    <Pagination
                      simple
                      current={currentPage}
                      pageSize={pageSize}
                      total={displayData.length}
                      onChange={setCurrentPage}
                    />
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '20px', color: token.colorTextSecondary }}>
                  暂无数据
                </div>
              )}
            </div>
          ) : (
            <Table
              dataSource={displayData}
              columns={columns}
              rowKey="id"
              size="middle"
              pagination={{
                current: currentPage,
                pageSize: pageSize,
                total: displayData.length,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (p, s) => { setCurrentPage(p); setPageSize(s); }
              }}
            />
          )}
        </Spin>
      </Card>

      <Modal
        title="新增记录"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={500}
        zIndex={1050}
      >
        <Form form={form} layout="vertical" onFinish={handleFinish} style={{ marginTop: 20 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="type" label="收/支" rules={[{ required: true }]}>
                <Radio.Group
                  onChange={e => {
                    setCurrentType(e.target.value);
                    form.setFieldsValue({ categoryAccountId: undefined });
                  }}
                  value={currentType}
                  buttonStyle="solid"
                  style={{ width: '100%' }}
                >
                  <Radio.Button value="expense" style={{ width: '50%', textAlign: 'center' }}>支出</Radio.Button>
                  <Radio.Button value="income" style={{ width: '50%', textAlign: 'center' }}>收入</Radio.Button>
                </Radio.Group>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="categoryAccountId" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
                <Select placeholder="请选择" options={categoryOptions} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="amount" label="金额" rules={[{ required: true }]}>
            <InputNumber prefix="¥" style={{ width: '100%' }} size="large" min={0.01} precision={2} />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} showCount maxLength={50} />
          </Form.Item>
          <Form.Item name="timestamp" label="日期" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large" style={{ marginTop: 10 }}>保存</Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default HomePage;
