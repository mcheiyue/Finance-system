import React, { useState, useEffect, useCallback } from 'react';
import {
  Button, Table, Modal, Form, Select, InputNumber,
  Spin, Card, Tag, Popconfirm, Row, Col, Space, Progress,
  theme, Typography, App, Grid
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, WalletOutlined,
  WarningOutlined, CheckCircleOutlined, ExclamationCircleOutlined
} from '@ant-design/icons';
import { getAccounts } from '../api/account';
import { getBudgets, getBudgetExecution, createBudget, deleteBudget } from '../api/budget';

const { Text } = Typography;

function BudgetsPage() {
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();

  const [budgets, setBudgets] = useState([]);
  const [execution, setExecution] = useState([]);
  const [expenseAccounts, setExpenseAccounts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  const loadAccounts = useCallback(async () => {
    try {
      const data = await getAccounts('EXPENSE');
      setExpenseAccounts(data || []);
    } catch (error) {
      console.error('加载账户失败:', error);
    }
  }, []);

  const loadBudgets = useCallback(async () => {
    setLoading(true);
    try {
      const [budgetData, executionData] = await Promise.all([
        getBudgets(),
        getBudgetExecution()
      ]);
      setBudgets(budgetData || []);
      setExecution(executionData || []);
    } catch (error) {
      console.error('加载预算数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAccounts();
    loadBudgets();
  }, [loadAccounts, loadBudgets]);

  const getExecution = useCallback((accountId) => {
    return execution.find(e => e.accountId === accountId);
  }, [execution]);

  const getAccountName = useCallback((accountId) => {
    const account = expenseAccounts.find(a => a.id === accountId);
    return account ? account.name : accountId;
  }, [expenseAccounts]);

  const handleCreate = async (values) => {
    try {
      await createBudget({
        accountId: values.accountId,
        limitAmount: values.limitAmount
      });
      message.success('预算创建成功');
      setModalVisible(false);
      form.resetFields();
      loadBudgets();
    } catch (error) {
      console.error('创建预算失败:', error);
    }
  };

  const handleDelete = useCallback(async (id) => {
    try {
      await deleteBudget(id);
      message.success('预算删除成功');
      loadBudgets();
    } catch (error) {
      console.error('删除预算失败:', error);
    }
  }, [loadBudgets, message]);

  const showCreateModal = () => {
    form.resetFields();
    setModalVisible(true);
  };

  const getUsageStatus = (percent) => {
    if (percent > 100) return 'exception';
    if (percent > 80) return 'active';
    return 'success';
  };

  const getUsageTag = (percent) => {
    if (percent > 100) {
      return <Tag color="error" icon={<ExclamationCircleOutlined />}>超支</Tag>;
    }
    if (percent > 80) {
      return <Tag color="warning" icon={<WarningOutlined />}>预警</Tag>;
    }
    return <Tag color="success" icon={<CheckCircleOutlined />}>正常</Tag>;
  };

  const summary = budgets.reduce((acc, budget) => {
    const exec = getExecution(budget.accountId);
    const usedAmount = exec ? exec.usedAmount : 0;
    const remaining = budget.limitAmount - usedAmount;
    return {
      totalBudget: acc.totalBudget + budget.limitAmount,
      totalUsed: acc.totalUsed + usedAmount,
      totalRemaining: acc.totalRemaining + remaining
    };
  }, { totalBudget: 0, totalUsed: 0, totalRemaining: 0 });

  const availableAccounts = expenseAccounts.filter(account =>
    !budgets.some(budget => budget.accountId === account.id)
  );

  const columns = [
    {
      title: '分类', key: 'category', width: 140, align: 'left',
      render: (_, record) => {
        const name = getAccountName(record.accountId);
        return <Text strong>{name}</Text>;
      }
    },
    {
      title: '预算金额', dataIndex: 'limitAmount', align: 'right', width: 130,
      render: (amount) => (
        <span className="font-mono" style={{ fontWeight: 600, fontSize: 15 }}>
          ¥{Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
        </span>
      )
    },
    {
      title: '已用', key: 'usedAmount', align: 'right', width: 130,
      render: (_, record) => {
        const exec = getExecution(record.accountId);
        const usedAmount = exec ? exec.usedAmount : 0;
        return (
          <span className="font-mono" style={{
            color: usedAmount > record.limitAmount ? token.colorError : token.colorText,
            fontWeight: 600,
            fontSize: 15
          }}>
            ¥{Number(usedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
          </span>
        );
      }
    },
    {
      title: '剩余', key: 'remaining', align: 'right', width: 130,
      render: (_, record) => {
        const exec = getExecution(record.accountId);
        const usedAmount = exec ? exec.usedAmount : 0;
        const remaining = record.limitAmount - usedAmount;
        return (
          <span className="font-mono" style={{
            color: remaining < 0 ? token.colorError : token.colorSuccess,
            fontWeight: 600,
            fontSize: 15
          }}>
            ¥{Number(remaining).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
          </span>
        );
      }
    },
    {
      title: '使用率', key: 'usage', width: 200, align: 'center',
      render: (_, record) => {
        const exec = getExecution(record.accountId);
        const usedAmount = exec ? exec.usedAmount : 0;
        const percent = record.limitAmount > 0 ? Math.round((usedAmount / record.limitAmount) * 100) : 0;
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Progress
              percent={Math.min(percent, 100)}
              size="small"
              status={getUsageStatus(percent)}
              showInfo={false}
              style={{ flex: 1, margin: 0 }}
            />
            {getUsageTag(percent)}
            <Text type="secondary" style={{ fontSize: 12, minWidth: 40 }}>{percent}%</Text>
          </div>
        );
      }
    },
    {
      title: '操作', key: 'action', width: 100, align: 'center',
      render: (_, record) => (
        <Popconfirm
          title="确认删除此预算?"
          description="删除后将无法恢复"
          onConfirm={() => handleDelete(record.id)}
          okText="确认"
          cancelText="取消"
        >
          <Button type="text" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      )
    }
  ];

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', marginTop: 24 }}>
      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ color: token.colorTextSecondary, fontSize: 13, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>总预算</div>
                <div className="font-mono" style={{ fontSize: 28, fontWeight: 'bold', color: token.colorText }}>¥{Number(summary.totalBudget).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
              </div>
              <div style={{ fontSize: 24, color: token.colorPrimary }}><WalletOutlined /></div>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ color: token.colorTextSecondary, fontSize: 13, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>已使用</div>
                <div className="font-mono" style={{ fontSize: 28, fontWeight: 'bold', color: token.colorError }}>¥{Number(summary.totalUsed).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
              </div>
              <div style={{ fontSize: 24, color: token.colorError }}><ExclamationCircleOutlined /></div>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ color: token.colorTextSecondary, fontSize: 13, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>剩余</div>
                <div className="font-mono" style={{ fontSize: 28, fontWeight: 'bold', color: token.colorSuccess }}>¥{Number(summary.totalRemaining).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
              </div>
              <div style={{ fontSize: 24, color: token.colorSuccess }}><CheckCircleOutlined /></div>
            </div>
          </Card>
        </Col>
      </Row>

      <Card
        variant="borderless"
        title={<Space><WalletOutlined /><span>预算管理</span></Space>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
            新增预算
          </Button>
        }
        style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}` }}
      >
        <Spin spinning={loading}>
          {!screens.md ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {budgets.length > 0 ? (
                budgets.map((budget) => {
                  const exec = getExecution(budget.accountId);
                  const usedAmount = exec ? exec.usedAmount : 0;
                  const remaining = budget.limitAmount - usedAmount;
                  const percent = budget.limitAmount > 0 ? Math.round((usedAmount / budget.limitAmount) * 100) : 0;
                  const accountName = getAccountName(budget.accountId);

                  return (
                    <Card key={budget.id} size="small" style={{ width: '100%', borderRadius: 8 }} styles={{ body: { padding: 12 } }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                        <Text strong style={{ fontSize: 16 }}>{accountName}</Text>
                        {getUsageTag(percent)}
                      </div>
                      <Row gutter={[8, 8]} style={{ marginBottom: 8 }}>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>预算</div>
                          <div className="font-mono" style={{ fontWeight: 600 }}>
                            ¥{Number(budget.limitAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                          </div>
                        </Col>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>已用</div>
                          <div className="font-mono" style={{ fontWeight: 600, color: usedAmount > budget.limitAmount ? token.colorError : token.colorText }}>
                            ¥{Number(usedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                          </div>
                        </Col>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>剩余</div>
                          <div className="font-mono" style={{ fontWeight: 600, color: remaining < 0 ? token.colorError : token.colorSuccess }}>
                            ¥{Number(remaining).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                          </div>
                        </Col>
                      </Row>
                      <div style={{ marginBottom: 8 }}>
                        <Progress
                          percent={Math.min(percent, 100)}
                          size="small"
                          status={getUsageStatus(percent)}
                          format={() => `${percent}%`}
                        />
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <Popconfirm
                          title="确认删除此预算?"
                          onConfirm={() => handleDelete(budget.id)}
                          okText="确认"
                          cancelText="取消"
                        >
                          <Button size="small" type="text" danger icon={<DeleteOutlined />}>删除</Button>
                        </Popconfirm>
                      </div>
                    </Card>
                  );
                })
              ) : (
                <div style={{ textAlign: 'center', padding: 20, color: token.colorTextSecondary }}>
                  暂无预算数据
                </div>
              )}
            </div>
          ) : (
            <Table
              dataSource={budgets}
              columns={columns}
              rowKey="id"
              size="middle"
              pagination={false}
              locale={{ emptyText: '暂无预算数据' }}
            />
          )}
        </Spin>
      </Card>

      <Modal
        title="新增预算"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={500}
        zIndex={1050}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate} style={{ marginTop: 20 }}>
          <Form.Item
            name="accountId"
            label="支出分类"
            rules={[{ required: true, message: '请选择支出分类' }]}
          >
            <Select
              placeholder="请选择支出分类"
              options={availableAccounts.map(a => ({ label: a.name, value: a.id }))}
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>
          <Form.Item
            name="limitAmount"
            label="预算金额"
            rules={[{ required: true, message: '请输入预算金额' }]}
          >
            <InputNumber
              prefix="¥"
              style={{ width: '100%' }}
              size="large"
              min={0.01}
              precision={2}
              placeholder="请输入预算金额"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large" style={{ marginTop: 10 }}>
              创建预算
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default BudgetsPage;
