import React, { useState, useEffect } from 'react';
import {
  Tabs, Spin, Alert, Card, Button, Modal, Form, Select, InputNumber,
  message, Empty, Row, Col, theme, Space
} from 'antd';
import { Pie, Area } from '@ant-design/plots';
import {
  SettingOutlined, PieChartOutlined, LineChartOutlined,
  ArrowUpOutlined, ArrowDownOutlined, WalletOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { EXPENSE_CATEGORIES } from '../constants';
import { useTheme } from '../ThemeContext';

const { Option } = Select;

function StatisticsPage() {
  const { token } = theme.useToken();
  const { isDarkMode } = useTheme();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [typeData, setTypeData] = useState([]);
  const [categoryData, setCategoryData] = useState([]);
  const [trendData, setTrendData] = useState([]);
  const [summary, setSummary] = useState({ totalIncome: 0, totalExpense: 0 });
  const [budgetModalVisible, setBudgetModalVisible] = useState(false);
  const [budgetForm] = Form.useForm();


  const loadData = async () => {
    setLoading(true);
    try {
      const [typeRes, trendRes] = await Promise.all([
        axios.get('/api/transactions/stats/type'),
        axios.get('/api/transactions/stats/recent?days=30')
      ]);

      const tData = Object.entries(typeRes.data).map(([type, total]) => ({
        type: type === 'income' ? '收入' : '支出',
        total: Number(total),
      }));
      setTypeData(tData);
      setSummary({ totalIncome: Number(typeRes.data.income || 0), totalExpense: Number(typeRes.data.expense || 0) });

      const dateMap = {};
      trendRes.data.forEach(tx => {
        const date = tx.timestamp.split('T')[0];
        if (!dateMap[date]) dateMap[date] = { income: 0, expense: 0 };
        if (tx.type === 'income') dateMap[date].income += Number(tx.amount);
        else dateMap[date].expense += Number(tx.amount);
      });
      const trData = Object.entries(dateMap).map(([date, val]) => ({
        date, income: val.income, expense: val.expense, net: val.income - val.expense
      })).sort((a, b) => a.date.localeCompare(b.date));
      setTrendData(trData);
      await loadCategoryStats('expense');
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const loadCategoryStats = async (type) => {
    try {
      const res = await axios.get('/api/transactions/stats/category', { params: { type } });
      setCategoryData(Object.entries(res.data).map(([c, t]) => ({ category: c, total: Number(t) })).sort((a, b) => b.total - a.total));
    } catch (e) { setError(e.message); }
  };

  useEffect(() => { loadData(); }, []);

  const handleBudgetFinish = (values) => {
    const budgets = JSON.parse(localStorage.getItem('finance_budgets') || '{}');
    budgets[values.category] = values.limit;
    localStorage.setItem('finance_budgets', JSON.stringify(budgets));
    message.success('预算设置成功');
    setBudgetModalVisible(false);
  };
  const monoColors = ['#111827', '#374151', '#4B5563', '#6B7280', '#9CA3AF', '#D1D5DB'];
  const darkMonoColors = ['#F9FAFB', '#E5E7EB', '#D1D5DB', '#9CA3AF', '#6B7280', '#4B5563'];
  const chartColors = isDarkMode ? darkMonoColors : monoColors;

  
  const totalTypeAmount = typeData.reduce((sum, item) => sum + item.total, 0);

  const typeConfig = {
    data: typeData,
    angleField: 'total',
    colorField: 'type',
    radius: 0.8,
    innerRadius: 0.7,
    
    scale: {
      color: {
        domain: ['收入', '支出'],
        range: [token.colorSuccess, token.colorError],
      },
    },
    label: {
      
      text: (d) => {
        const percent = totalTypeAmount > 0 ? d.total / totalTypeAmount : 0;
        return `${d.type} ${(percent * 100).toFixed(0)}%`;
      },
      position: 'outside', 
      style: {
        fontSize: 12,
        fill: token.colorTextSecondary,
        fontWeight: 'bold',
      },
      connector: true, 
    },
    
    legend: {
      color: {
        position: 'bottom',
        layout: { justifyContent: 'center' }, 
        itemLabelFill: token.colorText,       
      },
    },
    style: {
      lineWidth: 2,
      stroke: token.colorBgContainer,
    },
    tooltip: {
      title: 'type',
      items: [{ channel: 'y', valueFormatter: (d) => `¥${Number(d).toFixed(2)}` }]
    },
    interaction: {
      elementHighlight: true, 
    },
  };

  
  const totalCategoryAmount = categoryData.reduce((sum, item) => sum + item.total, 0);

  const categoryConfig = {
    data: categoryData,
    angleField: 'total',
    colorField: 'category',
    radius: 0.8,
    scale: {
      color: {
        range: chartColors, 
      },
    },
    label: {
      
      text: (d) => {
        const percent = totalCategoryAmount > 0 ? d.total / totalCategoryAmount : 0;
        return `${d.category} ${(percent * 100).toFixed(0)}%`;
      },
      position: 'outside',
      autoRotate: false,
      style: {
        fill: token.colorTextSecondary,
      },
      connector: true,
    },
    
    legend: {
      color: {
        position: 'bottom',
        layout: { justifyContent: 'center' },
        itemLabelFill: token.colorText,
      },
    },
    style: {
      lineWidth: 2,
      stroke: token.colorBgContainer,
    },
    tooltip: {
      title: 'category',
      items: [{ channel: 'y', valueFormatter: (d) => `¥${Number(d).toFixed(2)}` }]
    },
    interaction: {
      elementHighlight: true,
    },
  };

  const trendConfig = {
    data: trendData,
    xField: 'date',
    yField: 'net',
    shapeField: 'smooth',

    style: {
      fill: `linear-gradient(to bottom, ${token.colorPrimary} 60%, ${token.colorBgContainer} 100%)`,
      fillOpacity: 0.25,
      stroke: token.colorPrimary,
      lineWidth: 2,
    },

    axis: {
      y: {
        grid: {
          line: {
            style: {
              lineDash: [4, 4],
              stroke: token.colorBorderSecondary,
              strokeOpacity: 0.6,
            }
          }
        }
      },
      
      x: {
        line: null,
        tick: null,
        
        labelTransform: 'rotate(0)',
        
        labelAutoHide: true,
      }
    },

    tooltip: {
      title: (d) => d.date,
      items: [
        (d) => ({
          name: '收入',
          value: `+${Number(d.income).toFixed(2)}`,
          color: token.colorSuccess
        }),
        (d) => ({
          name: '支出',
          value: `-${Number(d.expense).toFixed(2)}`,
          color: token.colorError
        }),
        (d) => ({
          name: '净额',
          value: `${Number(d.net).toFixed(2)}`,
          color: token.colorPrimary
        }),
      ],
    },

    interaction: {
      tooltip: {
        marker: false,
      },
    },
  };

  const StatCard = ({ title, value, color, icon }) => (
    <Card variant="borderless" styles={{ body: { padding: 24 } }} style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}`, height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ color: token.colorTextSecondary, fontSize: 13, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 }}>{title}</div>
          <div className="font-mono" style={{ fontSize: 28, fontWeight: 'bold', color: token.colorText }}>¥{Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
        </div>
        <div style={{ fontSize: 24, color: color }}>{icon}</div>
      </div>
    </Card>
  );

  const tabItems = [
    {
      key: '1',
      label: <span><PieChartOutlined /> 收支构成</span>,
      children: (
        <Row gutter={[48, 24]}>
          <Col xs={24} lg={12}>
            <div style={{ textAlign: 'center', marginBottom: 16, fontWeight: 'bold', color: token.colorTextSecondary }}>收支类型</div>
            <div style={{ height: 350 }}>{typeData.length > 0 ? <Pie {...typeConfig} theme={isDarkMode ? 'dark' : 'light'} /> : <Empty />}</div>
          </Col>
          <Col xs={24} lg={12}>
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', marginBottom: 16, gap: 10 }}>
              <span style={{ fontWeight: 'bold', color: token.colorTextSecondary }}>分类占比</span>
              <Select defaultValue="expense" size="small" onChange={val => loadCategoryStats(val)} options={[{ value: 'expense', label: '支出' }, { value: 'income', label: '收入' }]} />
            </div>
            <div style={{ height: 350 }}>{categoryData.length > 0 ? <Pie {...categoryConfig} theme={isDarkMode ? 'dark' : 'light'} /> : <Empty />}</div>
          </Col>
        </Row>
      )
    },
    {
      key: '2',
      label: <span><LineChartOutlined /> 资产趋势</span>,
      children: <div style={{ height: 400, padding: '0 20px' }}><Area {...trendConfig} theme={isDarkMode ? 'dark' : 'light'} /></div>
    }
  ];

  return (
    <div>
      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12}><StatCard title="总收入" value={summary.totalIncome} color={token.colorSuccess} icon={<ArrowUpOutlined />} /></Col>
        <Col xs={24} sm={12}><StatCard title="总支出" value={summary.totalExpense} color={token.colorError} icon={<ArrowDownOutlined />} /></Col>
      </Row>

      <Card
        variant="borderless"
        style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}` }}
        title={<Space><WalletOutlined /><span>收支分析</span></Space>}
        extra={<Button type="primary" icon={<SettingOutlined />} onClick={() => setBudgetModalVisible(true)}>预算</Button>}
      >
        <Spin spinning={loading}><Tabs defaultActiveKey="1" items={tabItems} /></Spin>
      </Card>

      <Modal title="设置预算" open={budgetModalVisible} onCancel={() => setBudgetModalVisible(false)} footer={null}>
        <Alert title="超支后列表显示警告" type="info" showIcon style={{ marginBottom: 16 }} />
        <Form form={budgetForm} onFinish={handleBudgetFinish} layout="vertical">
          <Form.Item name="category" label="分类" rules={[{ required: true }]}><Select options={EXPENSE_CATEGORIES.map(c => ({ value: c, label: c }))} /></Form.Item>
          <Form.Item name="limit" label="上限金额" rules={[{ required: true }]}><InputNumber prefix="¥" style={{ width: '100%' }} /></Form.Item>
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>
    </div>
  );
}

export default StatisticsPage;