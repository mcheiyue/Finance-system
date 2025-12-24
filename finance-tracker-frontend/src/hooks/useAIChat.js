import { useState } from 'react';
import { App } from 'antd';
import { callAiApi } from '../services/aiService';
import { DEFAULT_AI_CONFIG } from '../constants';

export const useAIChat = (contextData, onActionReceived, onFilterReceived) => {
  const { message: msgApi } = App.useApp();

  const [drawerVisible, setDrawerVisible] = useState(false);
  const [configVisible, setConfigVisible] = useState(false);
  const [loading, setLoading] = useState(false);

  const [messages, setMessages] = useState([
    { role: 'assistant', content: '您好！我是您的 AI 财务助手。' }
  ]);

  const [config, setConfig] = useState(() => {
    const saved = localStorage.getItem('ai_chat_config');
    return saved ? JSON.parse(saved) : (DEFAULT_AI_CONFIG || { apiKey: '', baseUrl: '', model: '' });
  });

  const updateConfig = (newConfig) => {
    setConfig(newConfig);
    localStorage.setItem('ai_chat_config', JSON.stringify(newConfig));
    msgApi.success('AI 配置已更新');
    setConfigVisible(false);
  };

  const sendMessage = async (text) => {
    if (!text.trim()) return;
    setMessages(prev => [...prev, { role: 'user', content: text }]);
    setLoading(true);

    try {
      const response = await callAiApi(text, messages, config, contextData);

      let finalContent = response;

      const filterRegex = /\[FILTER\]\s*(\{[\s\S]*?\})\s*\[\/FILTER\]/;
      const filterMatch = finalContent.match(filterRegex); 

      if (filterMatch) {
        try {
          const filterData = JSON.parse(filterMatch[1]);
          finalContent = finalContent.replace(filterRegex, '').trim();

          if (onFilterReceived) onFilterReceived(filterData);
        } catch (e) {
          console.error('Filter parse error', e);
        }
      }

      const actionRegex = /\[ACTION\]\s*(\[[\s\S]*?\])\s*\[\/ACTION\]/;
      const actionMatch = finalContent.match(actionRegex); 

      const rawActionMatch = response.match(actionRegex);

      if (rawActionMatch) {
        try {
          const actionData = JSON.parse(rawActionMatch[1]);
          finalContent = finalContent.replace(actionRegex, '').trim();

          if (onActionReceived) onActionReceived(actionData);
        } catch (e) {
          try {
            const singleMatch = response.match(/\[ACTION\]\s*(\{[\s\S]*?\})\s*\[\/ACTION\]/);
            if (singleMatch) {
              const single = JSON.parse(singleMatch[1]);
              finalContent = finalContent.replace(/\[ACTION\]\s*(\{[\s\S]*?\})\s*\[\/ACTION\]/, '').trim();
              if (onActionReceived) onActionReceived([single]);
            }
          } catch (err) { }
        }
      }

      if (!finalContent && (rawActionMatch || filterMatch)) {
        finalContent = '已为您执行相关操作。';
      }

      setMessages(prev => [...prev, { role: 'assistant', content: finalContent }]);

    } catch (e) {
      msgApi.error(`AI Error: ${e.message}`);
      setMessages(prev => [...prev, { role: 'assistant', content: `❌ ${e.message}` }]);
    } finally {
      setLoading(false);
    }
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