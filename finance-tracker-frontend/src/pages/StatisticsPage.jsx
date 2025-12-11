import React, { useState, useEffect } from 'react';
import { 
  Tabs, Spin, Alert, Card, Button, Modal, Form, Select, InputNumber, 
  message, Empty, Row, Col, theme, Space 
} from 'antd';
import { Pie, Line } from '@ant-design/plots';
import { 
  SettingOutlined, PieChartOutlined, LineChartOutlined,
  ArrowUpOutlined, ArrowDownOutlined, WalletOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { EXPENSE_CATEGORIES } from '../constants';

const { Option } = Select;

function StatisticsPage() {
  const { token } = theme.useToken();
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
        axios.get('http://localhost:8080/api/transactions/stats/type'),
        axios.get('http://localhost:8080/api/transactions/stats/recent?days=30')
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
      const res = await axios.get('http://localhost:8080/api/transactions/stats/category', { params: { type } });
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
  const chartColors = token.mode === 'dark' ? darkMonoColors : monoColors;

  const typeConfig = {
    data: typeData,
    angleField: 'total',
    colorField: 'type',
    radius: 0.8,
    innerRadius: 0.7,
    color: ({ type }) => type === '收入' ? token.colorSuccess : token.colorError,
    statistic: null,
    label: { type: 'outer', content: '{name} {percentage}', style: { fontSize: 12, fill: token.colorTextSecondary } },
    interactions: [{ type: 'element-active' }],
    legend: { position: 'bottom', itemName: { style: { fill: token.colorText } } },
    pieStyle: { lineWidth: 2, stroke: token.colorBgContainer },
    tooltip: { formatter: (datum) => ({ name: datum.type, value: `¥${datum.total.toFixed(2)}` }) },
  };

  const categoryConfig = {
    data: categoryData,
    angleField: 'total',
    colorField: 'category',
    radius: 0.8,
    color: chartColors,
    label: { type: 'outer', content: '{name} {percentage}', autoRotate: false, style: { fill: token.colorTextSecondary } },
    interactions: [{ type: 'element-active' }],
    legend: { position: 'bottom', flipPage: false, itemName: { style: { fill: token.colorText } } },
    pieStyle: { lineWidth: 2, stroke: token.colorBgContainer },
    tooltip: { formatter: (datum) => ({ name: datum.category, value: `¥${datum.total.toFixed(2)}` }) },
  };

  const trendConfig = {
    data: trendData,
    xField: 'date',
    yField: 'net',
    smooth: true,
    color: token.colorPrimary,
    areaStyle: { fill: `l(270) 0:${token.colorBgContainer} 0.5:${token.colorPrimary} 1:${token.colorPrimary}`, fillOpacity: 0.1 }, 
    point: { size: 3, shape: 'circle' },
    yAxis: { grid: { line: { style: { lineDash: [2, 4], stroke: token.colorBorderSecondary } } } }, 
    tooltip: { 
      showContent: true,
      domStyles: {
        'g2-tooltip': {
          backgroundColor: token.colorBgElevated,
          color: token.colorText,
          boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
          border: `1px solid ${token.colorBorderSecondary}`,
          padding: 0, borderRadius: '8px', opacity: 0.95,
        },
      },
      customContent: (title, items) => {
        if (!items?.length) return '';
        const d = items[0].data;
        return `<div style="padding:12px; min-width:160px;">
          <div style="margin-bottom:8px;font-weight:bold;color:${token.colorTextSecondary}">${title}</div>
          <div style="display:flex;justify-content:space-between;"><span style="color:${token.colorSuccess}">收入</span><b class="font-mono">+${d.income}</b></div>
          <div style="display:flex;justify-content:space-between;"><span style="color:${token.colorError}">支出</span><b class="font-mono">-${d.expense}</b></div>
          <div style="border-top:1px solid ${token.colorBorderSecondary};margin-top:6px;padding-top:6px;font-weight:bold;display:flex;justify-content:space-between;"><span>净额</span><span class="font-mono">${d.net}</span></div>
        </div>`;
      }
    }
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
            <div style={{ height: 350 }}>{typeData.length > 0 ? <Pie {...typeConfig} /> : <Empty />}</div>
          </Col>
          <Col xs={24} lg={12}>
            <div style={{ display:'flex', justifyContent:'center', alignItems:'center', marginBottom:16, gap:10 }}>
              <span style={{fontWeight:'bold', color:token.colorTextSecondary}}>分类占比</span>
              <Select defaultValue="expense" size="small" onChange={val => loadCategoryStats(val)} options={[{value:'expense',label:'支出'},{value:'income',label:'收入'}]} />
            </div>
            <div style={{ height: 350 }}>{categoryData.length > 0 ? <Pie {...categoryConfig} /> : <Empty />}</div>
          </Col>
        </Row>
      )
    },
    {
      key: '2',
      label: <span><LineChartOutlined /> 资产趋势</span>,
      children: <div style={{ height: 400, padding: '0 20px' }}><Line {...trendConfig} /></div>
    }
  ];

  return (
    <div>
      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        {/* 👉 1. 标题改回中文 */}
        <Col xs={24} sm={12}><StatCard title="总收入" value={summary.totalIncome} color={token.colorSuccess} icon={<ArrowUpOutlined />} /></Col>
        <Col xs={24} sm={12}><StatCard title="总支出" value={summary.totalExpense} color={token.colorError} icon={<ArrowDownOutlined />} /></Col>
      </Row>

      <Card 
        variant="borderless" 
        style={{ borderRadius: 8, border: `1px solid ${token.colorBorder}` }} 
        title={<Space><WalletOutlined/><span>收支分析</span></Space>} 
        extra={<Button type="primary" icon={<SettingOutlined />} onClick={() => setBudgetModalVisible(true)}>预算</Button>}
      >
        <Spin spinning={loading}><Tabs defaultActiveKey="1" items={tabItems} /></Spin>
      </Card>

      <Modal title="设置预算" open={budgetModalVisible} onCancel={() => setBudgetModalVisible(false)} footer={null}>
        <Alert title="超支后列表显示警告" type="info" showIcon style={{marginBottom:16}} />
        <Form form={budgetForm} onFinish={handleBudgetFinish} layout="vertical">
          <Form.Item name="category" label="分类" rules={[{ required: true }]}><Select options={EXPENSE_CATEGORIES.map(c=>({value:c,label:c}))}/></Form.Item>
          <Form.Item name="limit" label="上限金额" rules={[{ required: true }]}><InputNumber prefix="¥" style={{width:'100%'}}/></Form.Item>
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>
    </div>
  );
}

export default StatisticsPage;