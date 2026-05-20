import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Table, Select, Spin, Empty, Row, Col, Tag,
  theme, App, Button, Descriptions, Modal, Space, Grid
} from 'antd';
import {
  ArrowUpOutlined, ArrowDownOutlined, WalletOutlined,
  FundOutlined, EyeOutlined, CalendarOutlined, BarChartOutlined
} from '@ant-design/icons';
import { getMonthlyReports, getMonthlyReport } from '../api/report';
import { getAccounts } from '../api/account';

function ReportsPage() {
  const { token } = theme.useToken();
  const { message } = App.useApp();
  const screens = Grid.useBreakpoint();
  const [loading, setLoading] = useState(false);
  const [reports, setReports] = useState([]);
  const [selectedMonth, setSelectedMonth] = useState(null);
  const [selectedDetail, setSelectedDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [accounts, setAccounts] = useState([]);

  useEffect(() => {
    getAccounts().then(setAccounts).catch(() => {});
  }, []);

  const loadReports = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getMonthlyReports();
      setReports(data || []);
      if (data && data.length > 0) {
        setSelectedMonth((prev) => prev ?? data[0].month);
      }
    } catch (error) {
      console.error('加载月度报告列表失败:', error);
      message.error('加载月度报告列表失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  const currentReport = useMemo(() => {
    if (!selectedMonth) return null;
    return reports.find(r => r.month === selectedMonth) || null;
  }, [reports, selectedMonth]);

  useEffect(() => {
    let cancelled = false;

    if (!selectedMonth) {
      setSelectedDetail(null);
      setDetailLoading(false);
      return () => {
        cancelled = true;
      };
    }

    const fetchSelectedDetail = async () => {
      setDetailLoading(true);
      try {
        const data = await getMonthlyReport(selectedMonth);
        if (!cancelled) {
          setSelectedDetail(data || null);
        }
      } catch (error) {
        if (!cancelled) {
          console.error('加载月度报告详情失败:', error);
          message.error('加载月度报告详情失败');
        }
      } finally {
        if (!cancelled) {
          setDetailLoading(false);
        }
      }
    };

    fetchSelectedDetail();

    return () => {
      cancelled = true;
    };
  }, [message, selectedMonth]);

  const displayedReport = selectedDetail || currentReport;

  const monthOptions = useMemo(() => {
    return reports.map(r => ({
      value: r.month,
      label: r.month,
    }));
  }, [reports]);


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
          <CalendarOutlined style={{ color: month === selectedMonth ? token.colorPrimary : token.colorTextSecondary }} />
          <span style={{ fontWeight: month === selectedMonth ? 600 : 500 }}>{month}</span>
          {month === selectedMonth && <Tag color="blue">当前查看</Tag>}
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
      width: 120,
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={(event) => {
            event.stopPropagation();
            setSelectedMonth(record.month);
            setDetailModalVisible(true);
          }}
        >
          查看余额
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
      render: (value, record) => {
        const displayValue = (record.type === 'INCOME' || record.type === 'EQUITY')
          ? Math.abs(Number(value || 0))
          : Number(value || 0);
        return (
          <span className="font-mono" style={{ fontWeight: 600 }}>
            ¥{formatAmount(displayValue)}
          </span>
        );
      },
    },
  ];

  const getBalanceData = (record) => {
    if (!record || !record.accountBalances) return [];
    const balances = record.accountBalances;
    const entries = balances instanceof Map
      ? Array.from(balances.entries())
      : Object.entries(balances);
    
    return entries.map(([accountId, balance], index) => {
      const account = accounts.find(a => a.id === accountId);
      return {
        key: index,
        account: account ? account.name : accountId.substring(0, 8) + '…',
        balance,
        type: account ? account.type : null,
      };
    });
  };

  const getCategoryData = (record, field) => {
    if (!record || !record[field]) return [];
    return Object.entries(record[field])
      .map(([category, amount]) => ({ category, amount: Number(amount || 0) }))
      .sort((a, b) => b.amount - a.amount);
  };

  const StatCard = ({ title, value, color, icon, prefix }) => (
    <Card className="report-stat-card" variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
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
      <div style={{ marginBottom: 16, color: token.colorTextSecondary, display: 'flex', alignItems: 'center', gap: 8 }}>
        <CalendarOutlined />
        <span>当前查看月份：{selectedMonth ?? '未选择'}</span>
      </div>
      <div style={{ marginBottom: 24 }}>
        <Row gutter={[24, 24]}>
          <Col xs={24} sm={8}>
            <StatCard
              title="所选月份收入"
              value={displayedReport?.totalIncome || 0}
              color={token.colorSuccess}
              icon={<ArrowUpOutlined />}
              prefix="+¥"
            />
          </Col>
          <Col xs={24} sm={8}>
            <StatCard
              title="所选月份支出"
              value={displayedReport?.totalExpense || 0}
              color={token.colorError}
              icon={<ArrowDownOutlined />}
              prefix="-¥"
            />
          </Col>
          <Col xs={24} sm={8}>
            <StatCard
              title="所选月份净收入"
              value={displayedReport ? getNetIncome(displayedReport) : 0}
              color={displayedReport && getNetIncome(displayedReport) >= 0 ? token.colorSuccess : token.colorError}
              icon={<WalletOutlined />}
              prefix={displayedReport && getNetIncome(displayedReport) >= 0 ? '+' : ''}
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
            <span>历史月份导航</span>
          </Space>
        }
        extra={
          <Select
            placeholder="查看月份"
            style={{ width: 160 }}
            value={selectedMonth}
            onChange={(value) => setSelectedMonth(value)}
            options={monthOptions}
          />
        }
      >
        <Spin spinning={loading || detailLoading}>
          {reports.length > 0 ? (
            !screens.md ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {reports.map((item) => {
                  const net = getNetIncome(item);
                  const selected = item.month === selectedMonth;
                  return (
                    <Card
                      key={item.id || item.month}
                      size="small"
                      style={{
                        width: '100%',
                        borderRadius: 8,
                        border: `1px solid ${selected ? token.colorPrimary : token.colorBorder}`,
                        background: selected ? token.colorPrimaryBg : undefined,
                      }}
                      styles={{ body: { padding: '16px' } }}
                      hoverable
                      onClick={() => setSelectedMonth(item.month)}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                        <Space>
                          <CalendarOutlined style={{ color: selected ? token.colorPrimary : token.colorTextSecondary }} />
                          <span style={{ fontWeight: 600, fontSize: 16 }}>{item.month}</span>
                          {selected && <Tag color="blue">当前查看</Tag>}
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
                      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                        <Button
                          type="link"
                          icon={<EyeOutlined />}
                          onClick={(event) => {
                            event.stopPropagation();
                            setSelectedMonth(item.month);
                            setDetailModalVisible(true);
                          }}
                        >
                          查看余额
                        </Button>
                      </div>
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
                  style: {
                    cursor: 'pointer',
                    background: record.month === selectedMonth ? token.colorPrimaryBg : undefined,
                  },
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
            <span>{selectedMonth ?? '-'} 账户余额明细</span>
          </Space>
        }
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={500}
      >
        <Spin spinning={detailLoading}>
          {selectedDetail ? (
            <div>
              {selectedDetail.month !== new Date().toISOString().slice(0, 7) && (
                <div style={{ marginBottom: 12, padding: '8px 12px', background: token.colorWarningBg || '#fffbe6', borderRadius: 6, fontSize: 13, color: token.colorWarningText || '#d48806' }}>
                  注：追溯生成的历史月份余额明细可能受当前账户状态影响
                </div>
              )}
              <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
                <Descriptions.Item label="月份">
                  <Space>
                    <CalendarOutlined />
                    {selectedDetail.month}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="交易笔数">
                  <Tag color="blue">{selectedDetail.transactionCount || 0} 笔</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="储蓄率">
                  <span className="font-mono" style={{ fontWeight: 600 }}>
                    {formatAmount(selectedDetail.savingRate || 0)}%
                  </span>
                </Descriptions.Item>
              </Descriptions>

              <div style={{ marginBottom: 8, fontWeight: 500 }}>
                <WalletOutlined style={{ marginRight: 8 }} />
                账户余额
              </div>
              <Table
                dataSource={getBalanceData(selectedDetail)}
                columns={balanceColumns}
                pagination={false}
                size="small"
                rowKey="account"
                locale={{ emptyText: '暂无账户余额数据' }}
              />

              <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
                <Col xs={24} md={12}>
                  <div style={{ marginBottom: 8, fontWeight: 500, color: token.colorSuccess }}>
                    收入分类
                  </div>
                  <Space wrap>
                    {getCategoryData(selectedDetail, 'categoryIncome').length > 0 ? getCategoryData(selectedDetail, 'categoryIncome').map((item) => (
                      <Tag key={`income-${item.category}`} color="green">
                        {item.category} +¥{formatAmount(item.amount)}
                      </Tag>
                    )) : <span style={{ color: token.colorTextSecondary }}>暂无收入分类数据</span>}
                  </Space>
                </Col>
                <Col xs={24} md={12}>
                  <div style={{ marginBottom: 8, fontWeight: 500, color: token.colorError }}>
                    支出分类
                  </div>
                  <Space wrap>
                    {getCategoryData(selectedDetail, 'categoryExpense').length > 0 ? getCategoryData(selectedDetail, 'categoryExpense').map((item) => (
                      <Tag key={`expense-${item.category}`} color="red">
                        {item.category} -¥{formatAmount(item.amount)}
                      </Tag>
                    )) : <span style={{ color: token.colorTextSecondary }}>暂无支出分类数据</span>}
                  </Space>
                </Col>
              </Row>
            </div>
          ) : (
            !detailLoading && <Empty description="暂无月度报告详情" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Spin>
      </Modal>
    </div>
  );
}

export default ReportsPage;
