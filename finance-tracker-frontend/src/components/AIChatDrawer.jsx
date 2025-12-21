import React, { useRef, useEffect } from 'react';
import { Drawer, Button, Input, Space, Spin, Alert, theme } from 'antd';
import { RobotOutlined, SettingOutlined, SendOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';

const AIChatDrawer = ({ 
  visible, 
  onClose, 
  onOpenConfig, 
  messages, 
  loading, 
  onSend,
  hasConfig 
}) => {
  const [input, setInput] = React.useState('');
  const { token } = theme.useToken();
  
  const messagesEndRef = useRef(null);
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };
  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = () => {
    if (!input.trim()) return;
    onSend(input);
    setInput('');
  };

  return (
    <Drawer
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space><RobotOutlined /><span>AI 财务助手</span></Space>
          <Button type="text" icon={<SettingOutlined />} onClick={onOpenConfig} />
        </div>
      }
      open={visible}
      onClose={onClose}
      zIndex={1100}
      width={400}
      styles={{ body: { display: 'flex', flexDirection: 'column', padding: 0 } }}
    >
      <style>{`
        .ai-markdown-body p { margin: 0 0 0.5em 0; }
        .ai-markdown-body p:last-child { margin-bottom: 0; }
        .ai-markdown-body ul, .ai-markdown-body ol { margin: 0 0 0.5em 0; padding-left: 20px; }
        .ai-markdown-body li { margin-bottom: 0.25em; }
        .ai-markdown-body li:last-child { margin-bottom: 0; }
      `}</style>

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px', background: token.colorBgLayout }}>
        {!hasConfig && <Alert message="请先配置 API Key" type="warning" showIcon style={{ marginBottom: 16 }} />}
        
        {messages.map((msg, i) => {
          const isUser = msg.role === 'user';
          return (
            <div key={i} style={{ marginBottom: 16, textAlign: isUser ? 'right' : 'left' }}>
              <div style={{
                display: 'inline-block', 
                padding: '10px 14px', 
                borderRadius: '12px', 
                maxWidth: '90%',
                textAlign: 'left',
                background: isUser ? '#141414' : token.colorBgContainer,
                color: isUser ? '#ffffff' : token.colorText,
                border: isUser ? '1px solid #424242' : `1px solid ${token.colorBorder}`,
                boxShadow: '0 2px 6px rgba(0,0,0,0.05)',
                lineHeight: 1.5 
              }}>
                {isUser ? (
                  msg.content
                ) : (
                  <div className="ai-markdown-body">
                    <ReactMarkdown>{msg.content}</ReactMarkdown>
                  </div>
                )}
              </div>
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      <div style={{ padding: 16, borderTop: `1px solid ${token.colorBorderSecondary}`, background: token.colorBgContainer }}>
        <Space.Compact style={{ width: '100%' }}>
          <Input 
            value={input} 
            onChange={e => setInput(e.target.value)} 
            onPressEnter={handleSend} 
            disabled={loading}
            placeholder="输入消息..." 
          />
          <Button type="primary" icon={loading ? <Spin size="small" /> : <SendOutlined />} onClick={handleSend} />
        </Space.Compact>
      </div>
    </Drawer>
  );
};

export default AIChatDrawer;