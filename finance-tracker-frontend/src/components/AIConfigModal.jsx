import React, { useEffect } from 'react';
import { Modal, Form, Input, message } from 'antd';

const AIConfigModal = ({ visible, onClose, onSave, initialValues }) => {
  const [form] = Form.useForm();

  useEffect(() => {
    if (visible) {
      form.setFieldsValue(initialValues);
    }
  }, [visible, initialValues, form]);

  const handleOk = () => {
    form.validateFields().then((values) => {
      onSave(values);
      onClose();
    }).catch((info) => {
      console.log('Validate Failed:', info);
    });
  };

  return (
    <Modal
      title="AI 配置 (本地存储)"
      open={visible}
      onCancel={onClose}
      onOk={handleOk}
      zIndex={1200} 
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
      >
        <Form.Item
          label="API Key"
          name="apiKey"
          rules={[{ required: true, message: '请输入 API Key' }]}
          tooltip="您的密钥仅存储在浏览器本地 LocalStorage 中，不会上传到服务器"
        >
          <Input.Password placeholder="sk-..." />
        </Form.Item>

        <Form.Item
          label="Base URL"
          name="baseUrl"
          rules={[{ required: true, message: '请输入接口地址' }]}
        >
          <Input placeholder="例如 https://api.openai.com/v1" />
        </Form.Item>

        <Form.Item
          label="模型名称 (Model)"
          name="model"
          rules={[{ required: true, message: '请输入模型名称' }]}
        >
          <Input placeholder="例如 gpt-3.5-turbo 或 deepseek-chat" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default AIConfigModal;