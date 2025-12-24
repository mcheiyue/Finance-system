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
      const responseRaw = await callAiApi(text, messages, config, contextData);

      let finalDisplayContent = responseRaw;

      const thoughtRegex = /\[THOUGHT\]([\s\S]*?)\[\/THOUGHT\]/;
      const thoughtMatch = finalContent.match(thoughtRegex);

      if (thoughtMatch) {
        console.log("🤔 AI 思维链:", thoughtMatch[1].trim());
        finalContent = finalContent.replace(thoughtRegex, '').trim();
      }

      const filterRegex = /\[FILTER\]\s*(\{[\s\S]*?\})\s*\[\/FILTER\]/;
      const filterMatch = responseRaw.match(filterRegex);
      if (filterMatch) {
        try {
          const filterData = JSON.parse(filterMatch[1]);
          finalDisplayContent = finalDisplayContent.replace(filterRegex, '').trim();
          if (onFilterReceived) onFilterReceived(filterData);
        } catch (e) { console.error('Filter parse error', e); }
      }

      const actionRegex = /\[ACTION\]\s*(\[[\s\S]*?\])\s*\[\/ACTION\]/;
      const actionMatch = responseRaw.match(actionRegex);
      const singleActionRegex = /\[ACTION\]\s*(\{[\s\S]*?\})\s*\[\/ACTION\]/;

      let actionData = null;
      if (actionMatch) {
        try { actionData = JSON.parse(actionMatch[1]); } catch (e) { }
        finalDisplayContent = finalDisplayContent.replace(actionRegex, '').trim();
      } else {
        const singleMatch = responseRaw.match(singleActionRegex);
        if (singleMatch) {
          try { actionData = [JSON.parse(singleMatch[1])]; } catch (e) { }
          finalDisplayContent = finalDisplayContent.replace(singleActionRegex, '').trim();
        }
      }

      if (actionData && onActionReceived) {
        onActionReceived(actionData);
      }

      if (!finalDisplayContent && (actionData || filterMatch)) {
        finalDisplayContent = '已为您执行操作。';
      }

      setMessages(prev => [...prev, { role: 'assistant', content: finalDisplayContent }]);

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