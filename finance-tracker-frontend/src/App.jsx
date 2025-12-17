import React, { useEffect } from 'react';
import { Routes, Route, Link, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Segmented, theme, Button, Dropdown, Space } from 'antd';
import {
  FileTextOutlined, PieChartOutlined, DollarOutlined, SunOutlined, MoonOutlined,
  DesktopOutlined, UserOutlined, LogoutOutlined, SafetyCertificateOutlined
} from '@ant-design/icons';
import HomePage from './pages/HomePage';
import StatisticsPage from './pages/StatisticsPage';
import AdminPage from './pages/AdminPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import { useTheme } from './ThemeContext';
import { useAuth } from './AuthContext';

const { Header, Content, Sider } = Layout;

const PrivateRoute = ({ children }) => {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" />;
};

export default function App() {
  const location = useLocation();
  const { themeMode, changeTheme, isDarkMode } = useTheme();
  const { token } = theme.useToken();
  const { user, logout } = useAuth();

  useEffect(() => {
    const path = location.pathname;
    let pageTitle = '个人理财系统';
    if (path === '/') pageTitle = '账单明细';
    else if (path === '/stats') pageTitle = '统计报表';
    else if (path === '/admin') pageTitle = '系统管理';
    else if (path === '/profile') pageTitle = '个人中心';
    document.title = `${pageTitle} - 个人理财系统`;
  }, [location]);

  const isAdmin = user?.roles?.some(r => {
    const roleName = typeof r === 'string' ? r : r.authority;
    return roleName === 'ROLE_ADMIN';
  });

  const menuItems = [
    { key: '1', icon: <FileTextOutlined />, label: <Link to="/">账单明细</Link> },
    { key: '2', icon: <PieChartOutlined />, label: <Link to="/stats">统计报表</Link> },
  ];

  if (isAdmin) {
    menuItems.push({
      key: '3', icon: <SafetyCertificateOutlined style={{ color: token.colorError }} />, label: <Link to="/admin">系统管理</Link>,
    });
  }

  const getSelectedKey = () => {
    const path = location.pathname;
    if (path === '/') return ['1'];
    if (path === '/stats') return ['2'];
    if (path === '/admin') return ['3'];
    return [];
  };

  const userMenu = {
    items: [
      { key: 'profile', label: <Link to="/profile">个人中心</Link>, icon: <UserOutlined /> },
      { type: 'divider' },
      { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, onClick: logout, danger: true }
    ]
  };

  if (location.pathname === '/login' || location.pathname === '/register') {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Routes>
    );
  }

  const headerStyle = {
    height: 64,
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    background: token.colorBgContainer,
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
    padding: '0 24px',
    zIndex: 20, transition: 'all 0.3s',

    position: 'sticky',
    top: 0,
    zIndex: 1001, // 确保层级高于内容，防止内容滚动时覆盖 Header
    width: '100%' // 确保宽度占满
  };

  return (
    <PrivateRoute>
      <Layout style={{ minHeight: '100vh' }}>
        <Header style={headerStyle}>
          <div style={{ display: 'flex', alignItems: 'center', color: token.colorText, fontSize: '16px', fontWeight: '600', letterSpacing: '-0.5px' }}>
            <DollarOutlined style={{ fontSize: '20px', marginRight: '10px' }} />
            个人理财系统
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <div className="theme-switcher">
              <Segmented
                value={themeMode} onChange={changeTheme}
                options={[
                  { value: 'light', icon: <SunOutlined /> },
                  { value: 'system', icon: <DesktopOutlined /> },
                  { value: 'dark', icon: <MoonOutlined /> },
                ]}
              />
            </div>
            <Dropdown menu={userMenu}>
              <Button type="text" style={{ color: token.colorText, display: 'flex', alignItems: 'center' }}>
                <Space>
                  <UserOutlined />
                  <span style={{ fontWeight: 500 }}>{user?.username}</span>
                  {isAdmin && <span style={{ fontSize: 10, border: `1px solid ${token.colorError}`, color: token.colorError, padding: '0 4px', borderRadius: 4, fontWeight: 'bold' }}>ADMIN</span>}
                </Space>
              </Button>
            </Dropdown>
          </div>
        </Header>

        <Layout>
          <Sider
            width={220}
            breakpoint="lg"
            collapsedWidth="0"
            onBreakpoint={(broken) => {
              console.log(broken);
            }}
            zeroWidthTriggerStyle={{ top: '10px' }}
            style={{
              background: token.colorBgLayout,
              borderRight: `1px solid ${token.colorBorderSecondary}`,
              position: 'fixed', height: '100vh', left: 0, zIndex: 100
            }}
          >
            <Menu
              mode="inline"
              selectedKeys={getSelectedKey()}
              style={{ height: '100%', borderRight: 0, padding: '16px 8px', background: 'transparent' }}
              items={menuItems.map(item => ({ ...item, style: { borderRadius: 6, marginBottom: 4 } }))}
            />
          </Sider>

          <Layout style={{ padding: '0', background: token.colorBgLayout, marginLeft: 220 }}>
            <Content className="site-layout-content" style={{ width: '100%', minHeight: 280, maxWidth: '1200px', margin: '0 auto' }}>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/stats" element={<StatisticsPage />} />
                {isAdmin && <Route path="/admin" element={<AdminPage />} />}
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="*" element={<Navigate to="/" />} />
              </Routes>
            </Content>
          </Layout>
        </Layout>
      </Layout>
    </PrivateRoute>
  );
}