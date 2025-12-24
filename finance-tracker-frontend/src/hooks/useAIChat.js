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

      // ============ 【新策略：优先使用 [REPLY] 分割】 ============
      if (finalDisplayContent.includes('[REPLY]')) {
        // 打印思考过程（用于调试）
        const parts = finalDisplayContent.split('[REPLY]');
        console.log("🤔 AI 思考过程 (Hidden):", parts[0].trim());
        
        // 只取 [REPLY] 之后的部分展示给用户
        finalDisplayContent = parts[1].trim();
      } else {
        // ============ 【备用策略：正则清洗】(防止 AI 忘了写 [REPLY]) ============
        const strictThoughtRegex = /\[THOUGHT\]([\s\S]*?)\[\/THOUGHT\]/i;
        const looseThoughtRegex = /^THOUGHT[\s\S]*?(?=\n\n|\[ACTION\]|\[FILTER\]|$)/i;

        let thoughtMatch = finalDisplayContent.match(strictThoughtRegex);
        if (thoughtMatch) {
          console.log("🤔 AI 思考 (Regex):", thoughtMatch[1].trim());
          finalDisplayContent = finalDisplayContent.replace(strictThoughtRegex, '').trim();
        } else {
          thoughtMatch = finalDisplayContent.match(looseThoughtRegex);
          if (thoughtMatch) {
            finalDisplayContent = finalDisplayContent.replace(looseThoughtRegex, '').trim();
          }
        }
      }

      // ============ 【通用清洗：去除漏网的 JSON 数据】 ============
      // 修复了之前的 SyntaxError (移除了无效的 'k' flag)
      const jsonLineRegex = /^\{.*"type".*?\}\s*(\(.*?\))?$/gm; 
      finalDisplayContent = finalDisplayContent.replace(jsonLineRegex, '').trim();
      
      // 清除常见的废话前缀
      finalDisplayContent = finalDisplayContent
        .replace(/^遍历交易记录[：:]/gm, '')
        .replace(/^筛选步骤[：:]/gm, '')
        .trim();

      // ============ 【指令处理】(逻辑不变) ============
      const filterRegex = /\[FILTER\]\s*(\{[\s\S]*?\})\s*\[\/FILTER\]/;
      const filterMatch = responseRaw.match(filterRegex); // 注意：用 responseRaw 匹配指令
      if (filterMatch) {
        try {
          const filterData = JSON.parse(filterMatch[1]);
          finalDisplayContent = finalDisplayContent.replace(filterRegex, '').trim();
          if (onFilterReceived) onFilterReceived(filterData);
        } catch (e) { console.error('Filter error', e); }
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

      // 兜底
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