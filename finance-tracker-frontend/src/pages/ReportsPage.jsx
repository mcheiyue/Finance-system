import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Table, Select, Spin, Empty, Row, Col, Statistic, Tag,
  theme, App, Button, Descriptions, Modal, Space, Grid
} from 'antd';
import {
  ArrowUpOutlined, ArrowDownOutlined, WalletOutlined,
  FundOutlined, EyeOutlined, CalendarOutlined, BarChartOutlined
} from '@ant-design/icons';
import { getMonthlyReports } from '../api/report';

function ReportsPage() {
  const { token } = theme.useToken();
  const { message } = App.useApp();
  const screens = Grid.useBreakpoint();
  const [loading, setLoading] = useState(false);
  const [reports, setReports] = useState([]);
  const [selectedMonth, setSelectedMonth] = useState(null);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [detailRecord, setDetailRecord] = useState(null);

  const loadReports = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getMonthlyReports();
      setReports(data || []);
      if (data && data.length > 0 && !selectedMonth) {
        setSelectedMonth(data[0].month);
      }
    } catch (error) {
      console.error('加载月度报告失败:', error);
      message.error('加载月度报告失败');
    } finally {
      setLoading(false);
    }
  }, [message, selectedMonth]);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  const currentReport = useMemo(() => {
    if (!selectedMonth) return null;
    return reports.find(r => r.month === selectedMonth) || null;
  }, [reports, selectedMonth]);

  const monthOptions = useMemo(() => {
    return reports.map(r => ({
      value: r.month,
      label: r.month,
    }));
  }, [reports]);

  const handleViewDetail = (record) => {
    setDetailRecord(record);
    setDetailModalVisible(true);
  };

  const formatAmount = (value) => {
    return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 });
  };

  const getNetIncome = (record) => {
    return Number(record.totalIncome || 0) - Number(record.totalExpense || 0);
  };

  const columns = [
    {
      title: '月份',
      dataIndex: 'month',
      key: 'month',
      width: 120,
      align: 'center',
      render: (month) => (
        <Space>
          <CalendarOutlined style={{ color: token.colorTextSecondary }} />
          <span style={{ fontWeight: 500 }}>{month}</span>
        </Space>
      ),
    },
    {
      title: '总收入',
      dataIndex: 'totalIncome',
      key: 'totalIncome',
      width: 150,
      align: 'right',
      sorter: (a, b) => Number(a.totalIncome) - Number(b.totalIncome),
      render: (value) => (
        <span className="font-mono" style={{ color: token.colorSuccess, fontWeight: 600 }}>
          +¥{formatAmount(value)}
        </span>
      ),
    },
    {
      title: '总支出',
      dataIndex: 'totalExpense',
      key: 'totalExpense',
      width: 150,
      align: 'right',
      sorter: (a, b) => Number(a.totalExpense) - Number(b.totalExpense),
      render: (value) => (
        <span className="font-mono" style={{ color: token.colorError, fontWeight: 600 }}>
          -¥{formatAmount(value)}
        </span>
      ),
    },
    {
      title: '净收入',
      key: 'net',
      width: 150,
      align: 'right',
      sorter: (a, b) => getNetIncome(a) - getNetIncome(b),
      render: (_, record) => {
        const net = getNetIncome(record);
        return (
          <span className="font-mono" style={{
            color: net >= 0 ? token.colorSuccess : token.colorError,
            fontWeight: 600
          }}>
            {net >= 0 ? '+' : ''}¥{formatAmount(net)}
          </span>
        );
      },
    },
    {
      title: '交易笔数',
      dataIndex: 'transactionCount',
      key: 'transactionCount',
      width: 100,
      align: 'center',
      sorter: (a, b) => (a.transactionCount || 0) - (b.transactionCount || 0),
      render: (value) => (
        <Tag color="blue">{value || 0} 笔</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleViewDetail(record)}
        >
          详情
        </Button>
      ),
    },
  ];

  const balanceColumns = [
    {
      title: '账户',
      dataIndex: 'account',
      key: 'account',
    },
    {
      title: '余额',
      dataIndex: 'balance',
      key: 'balance',
      align: 'right',
      render: (value) => (
        <span className="font-mono" style={{ fontWeight: 600 }}>
          ¥{formatAmount(value)}
        </span>
      ),
    },
  ];

  const getBalanceData = (record) => {
    if (!record || !record.accountBalances) return [];
    const balances = record.accountBalances;
    if (balances instanceof Map) {
      return Array.from(balances.entries()).map(([account, balance], index) => ({
        key: index,
        account,
        balance,
      }));
    }
    if (typeof balances === 'object') {
      return Object.entries(balances).map(([account, balance], index) => ({
        key: index,
        account,
        balance,
      }));
    }
    return [];
  };

  const StatCard = ({ title, value, color, icon, prefix }) => (
    <Card variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ color: token.colorTextSecondary, fontSize: 13, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>{title}</div>
          <div className="font-mono" style={{ fontSize: 28, fontWeight: 'bold', color: token.colorText }}>
            {prefix}{formatAmount(value)}
          </div>
        </div>
        <div style={{ fontSize: 24, color }}>{icon}</div>
      </div>
    </Card>
  );

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', marginTop: 24 }}>
      <div style={{ marginBottom: 24 }}>
        <Row gutter={[24, 24]}>
          <Col xs={24} sm={8}>
            <StatCard
              title="本月收入"
              value={currentReport?.totalIncome || 0}
              color={token.colorSuccess}
              icon={<ArrowUpOutlined />}
              prefix="+¥"
            />
          </Col>
          <Col xs={24} sm={8}>
            <StatCard
              title="本月支出"
              value={currentReport?.totalExpense || 0}
              color={token.colorError}
              icon={<ArrowDownOutlined />}
              prefix="-¥"
            />
          </Col>
          <Col xs={24} sm={8}>
            <StatCard
              title="本月净收入"
              value={currentReport ? getNetIncome(currentReport) : 0}
              color={currentReport && getNetIncome(currentReport) >= 0 ? token.colorSuccess : token.colorError}
              icon={<WalletOutlined />}
              prefix={currentReport && getNetIncome(currentReport) >= 0 ? '+' : ''}
            />
          </Col>
        </Row>
      </div>

      <Card
        variant="borderless"
        style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}` }}
        title={
          <Space>
            <BarChartOutlined />
            <span>月度报告</span>
          </Space>
        }
        extra={
          <Select
            placeholder="选择月份"
            style={{ width: 150 }}
            value={selectedMonth}
            onChange={setSelectedMonth}
            options={monthOptions}
            allowClear
          />
        }
      >
        <Spin spinning={loading}>
          {reports.length > 0 ? (
            !screens.md ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {reports.map((item) => {
                  const net = getNetIncome(item);
                  return (
                    <Card
                      key={item.id || item.month}
                      size="small"
                      style={{ width: '100%', borderRadius: 8, border: `1px solid ${token.colorBorder}` }}
                      styles={{ body: { padding: '16px' } }}
                      hoverable
                      onClick={() => handleViewDetail(item)}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                        <Space>
                          <CalendarOutlined style={{ color: token.colorPrimary }} />
                          <span style={{ fontWeight: 600, fontSize: 16 }}>{item.month}</span>
                        </Space>
                        <Tag color="blue">{item.transactionCount || 0} 笔</Tag>
                      </div>
                      <Row gutter={[12, 8]}>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>收入</div>
                          <div className="font-mono" style={{ color: token.colorSuccess, fontWeight: 600, fontSize: 14 }}>
                            +¥{formatAmount(item.totalIncome)}
                          </div>
                        </Col>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>支出</div>
                          <div className="font-mono" style={{ color: token.colorError, fontWeight: 600, fontSize: 14 }}>
                            -¥{formatAmount(item.totalExpense)}
                          </div>
                        </Col>
                        <Col span={8}>
                          <div style={{ color: token.colorTextSecondary, fontSize: 12 }}>净收入</div>
                          <div className="font-mono" style={{
                            color: net >= 0 ? token.colorSuccess : token.colorError,
                            fontWeight: 600,
                            fontSize: 14
                          }}>
                            {net >= 0 ? '+' : ''}¥{formatAmount(net)}
                          </div>
                        </Col>
                      </Row>
                    </Card>
                  );
                })}
              </div>
            ) : (
              <Table
                dataSource={reports}
                columns={columns}
                rowKey={(record) => record.id || record.month}
                size="middle"
                pagination={false}
                onRow={(record) => ({
                  onClick: () => setSelectedMonth(record.month),
                  style: { cursor: 'pointer' },
                })}
              />
            )
          ) : (
            !loading && <Empty description="暂无月度报告数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Spin>
      </Card>

      <Modal
        title={
          <Space>
            <FundOutlined />
            <span>{detailRecord?.month} 账户余额明细</span>
          </Space>
        }
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={500}
      >
        {detailRecord && (
          <div>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="月份">
                <Space>
                  <CalendarOutlined />
                  {detailRecord.month}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="总收入">
                <span className="font-mono" style={{ color: token.colorSuccess, fontWeight: 600 }}>
                  +¥{formatAmount(detailRecord.totalIncome)}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="总支出">
                <span className="font-mono" style={{ color: token.colorError, fontWeight: 600 }}>
                  -¥{formatAmount(detailRecord.totalExpense)}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="净收入">
                <span className="font-mono" style={{
                  color: getNetIncome(detailRecord) >= 0 ? token.colorSuccess : token.colorError,
                  fontWeight: 600
                }}>
                  {getNetIncome(detailRecord) >= 0 ? '+' : ''}¥{formatAmount(getNetIncome(detailRecord))}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="交易笔数">
                <Tag color="blue">{detailRecord.transactionCount || 0} 笔</Tag>
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginBottom: 8, fontWeight: 500 }}>
              <WalletOutlined style={{ marginRight: 8 }} />
              账户余额
            </div>
            <Table
              dataSource={getBalanceData(detailRecord)}
              columns={balanceColumns}
              pagination={false}
              size="small"
              rowKey="account"
              locale={{ emptyText: '暂无账户余额数据' }}
            />
          </div>
        )}
      </Modal>
    </div>
  );
}

export default ReportsPage;
