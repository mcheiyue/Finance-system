import React, { createContext, useContext, useState, useEffect } from 'react';
import { ConfigProvider, theme, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';

const ThemeContext = createContext();

export const useTheme = () => useContext(ThemeContext);

export const ThemeProvider = ({ children }) => {
  const [themeMode, setThemeMode] = useState(
    localStorage.getItem('finance_theme_mode') || 'system'
  );

  const [systemIsDark, setSystemIsDark] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e) => setSystemIsDark(e.matches);
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  const isDarkMode = themeMode === 'dark' || (themeMode === 'system' && systemIsDark);

  const changeTheme = (mode) => {
    setThemeMode(mode);
    localStorage.setItem('finance_theme_mode', mode);
  };

  useEffect(() => {
    const root = document.documentElement;
    if (isDarkMode) {
      root.style.setProperty('--bg-body', '#121212'); 
      root.style.setProperty('--bg-card', '#1C1C1E'); 
      root.style.setProperty('--border-color', '#2C2C2E'); 
      root.style.setProperty('--text-main', '#FFFFFF');
      root.style.setProperty('--text-muted', '#A1A1AA'); 
    } else {
      root.style.setProperty('--bg-body', '#F9FAFB'); 
      root.style.setProperty('--bg-card', '#FFFFFF');
      root.style.setProperty('--border-color', '#E5E7EB');
      root.style.setProperty('--text-main', '#111827');
      root.style.setProperty('--text-muted', '#6B7280');
    }
    
    document.body.style.backgroundColor = isDarkMode ? '#121212' : '#F9FAFB';
    document.documentElement.setAttribute('data-theme', isDarkMode ? 'dark' : 'light');
  }, [isDarkMode]);

  const themeConfig = {
    algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: isDarkMode ? '#FFFFFF' : '#1F2937',
      colorSuccess: isDarkMode ? '#34d399' : '#059669',
      colorError: isDarkMode ? '#fb7185' : '#e11d48',
      colorWarning: isDarkMode ? '#fbbf24' : '#d97706',
      colorBgBase: isDarkMode ? '#121212' : '#FFFFFF',
      colorBgLayout: isDarkMode ? '#121212' : '#F9FAFB',
      colorBgContainer: isDarkMode ? '#1C1C1E' : '#FFFFFF',
      colorText: isDarkMode ? '#FFFFFF' : '#111827',
      colorTextSecondary: isDarkMode ? '#A1A1AA' : '#6B7280', 
      colorTextPlaceholder: isDarkMode ? '#52525b' : '#9CA3AF',
      colorBorder: isDarkMode ? '#2C2C2E' : '#E5E7EB',
      colorBorderSecondary: isDarkMode ? '#2C2C2E' : '#E5E7EB',

      borderRadius: 8,
      controlHeight: 40,
      fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    },
    components: {
      Layout: { headerBg: 'transparent' },
      Card: {
        colorBgContainer: isDarkMode ? '#1C1C1E' : '#FFFFFF',
        borderRadiusLG: 12,
        boxShadowTertiary: 'none',
      },
      Table: {
        borderRadiusLG: 12,
        headerBg: 'transparent',
        headerSplitColor: 'transparent',
        borderColor: isDarkMode ? '#2C2C2E' : '#E5E7EB',
        headerColor: isDarkMode ? '#A1A1AA' : '#6B7280', 
      },
      Button: {
        borderRadius: 8,
        primaryColor: isDarkMode ? '#000000' : '#FFFFFF',
        defaultBorderColor: isDarkMode ? '#2C2C2E' : '#E5E7EB',
      },
      Input: {
        colorBgContainer: isDarkMode ? '#121212' : '#FFFFFF', 
        colorBorder: isDarkMode ? '#2C2C2E' : '#E5E7EB',
        activeBorderColor: isDarkMode ? '#FFFFFF' : '#1F2937',
      },
      Select: {
        selectorBg: isDarkMode ? '#121212' : '#FFFFFF',
        colorBorder: isDarkMode ? '#2C2C2E' : '#E5E7EB',
        colorPrimary: isDarkMode ? '#FFFFFF' : '#1F2937',
      },
      DatePicker: {
        colorBgContainer: isDarkMode ? '#121212' : '#FFFFFF',
        colorBorder: isDarkMode ? '#2C2C2E' : '#E5E7EB',
        colorPrimary: isDarkMode ? '#FFFFFF' : '#1F2937',
      },
      Modal: {
        contentBg: isDarkMode ? '#1C1C1E' : '#FFFFFF',
        headerBg: isDarkMode ? '#1C1C1E' : '#FFFFFF',
      }
    }
  };

  return (
    <ThemeContext.Provider value={{ themeMode, changeTheme, isDarkMode }}>
      <ConfigProvider locale={zhCN} theme={themeConfig}>
        <AntdApp>
          {children}
        </AntdApp>
      </ConfigProvider>
    </ThemeContext.Provider>
  );
};