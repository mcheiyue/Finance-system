// src/hooks/useAIChat.js
import { useState } from 'react';
import { App } from 'antd';
import { callAiApi } from '../services/aiService';
import { DEFAULT_AI_CONFIG } from '../constants';

export const useAIChat = () => {
  const { message: msgApi } = App.useApp();
  
  // UI 状态
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [configVisible, setConfigVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  
  // 聊天记录
  const [messages, setMessages] = useState([
    { role: 'assistant', content: '您好！我是您的 AI 财务助手。您可以直接告诉我您的消费情况，我会为您预填账单。' }
  ]);

  // 配置管理：优先读取本地存储，没有则使用默认配置
  const [config, setConfig] = useState(() => {
    const saved = localStorage.getItem('ai_chat_config');
    return saved ? JSON.parse(saved) : DEFAULT_AI_CONFIG;
  });

  // 更新配置
  const updateConfig = (newConfig) => {
    setConfig(newConfig);
    localStorage.setItem('ai_chat_config', JSON.stringify(newConfig));
    msgApi.success('AI 配置已更新');
    setConfigVisible(false);
  };

  // 发送消息核心逻辑
  const sendMessage = async (text, contextData) => {
    setMessages(prev => [...prev, { role: 'user', content: text }]);
    setLoading(true);

    let actionResult = null;

    try {
      const response = await callAiApi(text, messages, config, contextData);
      
      // 解析 [ACTION] 指令
      const actionRegex = /\[ACTION\]\s*(\{[\s\S]*?\})\s*\[\/ACTION\]/;
      const match = response.match(actionRegex);

      if (match) {
        try {
          const actionData = JSON.parse(match[1]);
          const cleanText = response.replace(actionRegex, '').trim();
          
          setMessages(prev => [...prev, { role: 'assistant', content: cleanText || '已为您准备好记账单' }]);
          
          // 返回解析出的动作数据，供 UI 层处理（如打开表单）
          actionResult = actionData;
        } catch (e) {
          console.error("Action Parse Error", e);
          setMessages(prev => [...prev, { role: 'assistant', content: response.replace(/\[ACTION\][\s\S]*?\[\/ACTION\]/g, '') }]);
        }
      } else {
        setMessages(prev => [...prev, { role: 'assistant', content: response }]);
      }
    } catch (e) {
      msgApi.error(`AI 请求失败: ${e.message}`);
      setMessages(prev => [...prev, { role: 'assistant', content: `⚠️ 错误: ${e.message}` }]);
    } finally {
      setLoading(false);
    }

    return actionResult;
  };

  return {
    drawerVisible, setDrawerVisible,
    configVisible, setConfigVisible,
    loading,
    messages,
    config,
    updateConfig,
    sendMessage
  };
};