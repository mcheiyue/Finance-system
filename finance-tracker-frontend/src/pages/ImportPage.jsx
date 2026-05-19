import React, { useState, useCallback } from 'react';
import {
  Card, Table, Upload, Button, Steps, Alert, Tag, Spin, Result,
  theme, Typography, App, Grid, Space, Descriptions, Statistic, Row, Col
} from 'antd';
import {
  UploadOutlined, InboxOutlined, CheckCircleOutlined,
  FileExcelOutlined, ArrowLeftOutlined, ArrowRightOutlined,
  ImportOutlined, ReloadOutlined
} from '@ant-design/icons';
import { previewCsv, confirmCsvImport } from '../api/import';

const { Dragger } = Upload;
const { Text } = Typography;

function ImportPage() {
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();

  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);

  const handleUpload = useCallback(async (file) => {
    setLoading(true);
    setSelectedFile(file);
    try {
      const data = await previewCsv(file);
      setPreviewData(data);
      message.success('CSV 预览加载成功');
    } catch {
      message.error('CSV 解析失败，请检查文件格式');
      setPreviewData(null);
    } finally {
      setLoading(false);
    }
    return false;
  }, [message]);

  const handleNext = useCallback(() => {
    setCurrentStep(1);
  }, []);

  const handleBack = useCallback(() => {
    setCurrentStep(0);
    setImportResult(null);
  }, []);

  const handleConfirmImport = useCallback(async () => {
    if (!previewData?.transactions) return;
    setLoading(true);
    try {
      const validTransactions = previewData.transactions.filter(t => t.valid);
      const result = await confirmCsvImport(validTransactions);
      setImportResult(result);
      message.success('导入成功');
    } catch {
      message.error('导入失败');
      setImportResult({ success: false });
    } finally {
      setLoading(false);
    }
  }, [previewData, message]);

  const handleReset = useCallback(() => {
    setCurrentStep(0);
    setPreviewData(null);
    setImportResult(null);
    setSelectedFile(null);
  }, []);

  const validTransactions = previewData?.transactions?.filter(t => t.valid) || [];
  const invalidTransactions = previewData?.transactions?.filter(t => !t.valid) || [];

  const previewColumns = [
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (text) => <Text>{text || '-'}</Text>
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (amount) => (
        <Text strong style={{ color: token.colorPrimary }}>
          ¥{Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
        </Text>
      )
    },
    {
      title: '来源账户',
      dataIndex: 'fromAccountName',
      width: 140,
      ellipsis: true,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    },
    {
      title: '目标账户',
      dataIndex: 'toAccountName',
      width: 140,
      ellipsis: true,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    },
    {
      title: '时间',
      dataIndex: 'timestamp',
      width: 170,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    },
    {
      title: '状态',
      dataIndex: 'valid',
      width: 80,
      align: 'center',
      render: (valid, record) => (
        valid
          ? <Tag color="success">有效</Tag>
          : <Tag color="error" title={record.error}>无效</Tag>
      )
    }
  ];

  const summaryColumns = [
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (text) => <Text>{text || '-'}</Text>
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (amount) => (
        <Text strong style={{ color: token.colorSuccess }}>
          ¥{Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
        </Text>
      )
    },
    {
      title: '来源账户',
      dataIndex: 'fromAccountName',
      width: 140,
      ellipsis: true,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    },
    {
      title: '目标账户',
      dataIndex: 'toAccountName',
      width: 140,
      ellipsis: true,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    },
    {
      title: '时间',
      dataIndex: 'timestamp',
      width: 170,
      render: (text) => <Text type="secondary">{text || '-'}</Text>
    }
  ];

  const stepsItems = [
    { title: '上传 CSV', icon: <FileExcelOutlined /> },
    { title: '确认导入', icon: <ImportOutlined /> }
  ];

  const renderStep0 = () => (
    <Spin spinning={loading}>
      {!previewData ? (
        <div>
          <Dragger
            accept=".csv"
            showUploadList={false}
            beforeUpload={handleUpload}
            disabled={loading}
            style={{
              padding: `${token.paddingLG}px 0`,
              borderColor: token.colorBorder,
              borderRadius: token.borderRadiusLG,
              background: token.colorBgLayout
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined style={{ color: token.colorPrimary }} />
            </p>
            <p className="ant-upload-text" style={{ fontSize: 16 }}>
              点击或拖拽 CSV 文件到此处上传
            </p>
            <p className="ant-upload-hint" style={{ color: token.colorTextSecondary }}>
              仅支持 .csv 格式文件
            </p>
          </Dragger>

          <Alert
            message="CSV 格式说明"
            description={
              <div>
                <Text>请确保 CSV 文件包含以下列（按顺序）：</Text>
                <div style={{
                  background: token.colorBgLayout,
                  padding: `${token.paddingSM}px ${token.padding}px`,
                  borderRadius: token.borderRadiusSM,
                  marginTop: token.marginSM,
                  fontFamily: 'monospace',
                  fontSize: 13
                }}>
                  description,amount,fromAccountName,toAccountName,timestamp
                </div>
                <ul style={{ marginTop: token.marginSM, paddingLeft: 20, color: token.colorTextSecondary }}>
                  <li><Text type="secondary">description: 交易描述</Text></li>
                  <li><Text type="secondary">amount: 金额（正数）</Text></li>
                  <li><Text type="secondary">fromAccountName: 来源账户名称</Text></li>
                  <li><Text type="secondary">toAccountName: 目标账户名称</Text></li>
                  <li><Text type="secondary">timestamp: 时间（格式：YYYY-MM-DD HH:mm:ss）</Text></li>
                </ul>
              </div>
            }
            type="info"
            showIcon
            style={{ marginTop: token.marginLG }}
          />
        </div>
      ) : (
        <div>
          <Row gutter={[16, 16]} style={{ marginBottom: token.marginLG }}>
            <Col xs={12} sm={8}>
              <Card size="small" variant="borderless" style={{ background: token.colorBgLayout }}>
                <Statistic
                  title="总行数"
                  value={previewData.totalRows}
                  valueStyle={{ fontSize: screens.sm ? 24 : 20 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8}>
              <Card size="small" variant="borderless" style={{ background: token.colorSuccessBg }}>
                <Statistic
                  title="有效行数"
                  value={previewData.validRows}
                  valueStyle={{ color: token.colorSuccess, fontSize: screens.sm ? 24 : 20 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8}>
              <Card size="small" variant="borderless" style={{ background: token.colorErrorBg }}>
                <Statistic
                  title="无效行数"
                  value={previewData.invalidRows}
                  valueStyle={{ color: token.colorError, fontSize: screens.sm ? 24 : 20 }}
                />
              </Card>
            </Col>
          </Row>

          {invalidTransactions.length > 0 && (
            <Alert
              message={`${invalidTransactions.length} 条记录解析失败`}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {invalidTransactions.slice(0, 5).map((item, index) => (
                    <li key={index}>
                      <Text type="danger">第 {previewData.transactions.indexOf(item) + 1} 行: {item.error || '未知错误'}</Text>
                    </li>
                  ))}
                  {invalidTransactions.length > 5 && (
                    <li><Text type="secondary">...还有 {invalidTransactions.length - 5} 条错误</Text></li>
                  )}
                </ul>
              }
              type="error"
              showIcon
              style={{ marginBottom: token.marginLG }}
            />
          )}

          <Card
            title={`有效交易记录 (${validTransactions.length} 条)`}
            size="small"
            variant="borderless"
            style={{ marginBottom: token.marginLG }}
          >
            <Table
              dataSource={validTransactions}
              columns={previewColumns}
              rowKey={(_, index) => index}
              size="small"
              scroll={{ x: 700 }}
              pagination={{
                pageSize: 10,
                showSizeChanger: false,
                showTotal: (total) => `共 ${total} 条`
              }}
            />
          </Card>

          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重新选择文件
            </Button>
            <Button
              type="primary"
              icon={<ArrowRightOutlined />}
              onClick={handleNext}
              disabled={validTransactions.length === 0}
            >
              下一步
            </Button>
          </div>
        </div>
      )}
    </Spin>
  );

  const renderStep1 = () => (
    <Spin spinning={loading}>
      {importResult ? (
        <Result
          status={importResult.success === false ? 'error' : 'success'}
          title={importResult.success === false ? '导入失败' : '导入成功'}
          subTitle={
            importResult.success === false
              ? '交易记录导入过程中出现错误，请重试'
              : `已成功导入 ${validTransactions.length} 条交易记录`
          }
          extra={[
            <Button key="reset" icon={<ReloadOutlined />} onClick={handleReset}>
              继续导入
            </Button>
          ]}
        />
      ) : (
        <div>
          <Alert
            message="请确认以下信息"
            description={`即将导入 ${validTransactions.length} 条有效交易记录，${invalidTransactions.length > 0 ? `另有 ${invalidTransactions.length} 条无效记录将被跳过，` : ''}请确认无误后点击"确认导入"。`}
            type="info"
            showIcon
            style={{ marginBottom: token.marginLG }}
          />

          <Descriptions
            bordered
            column={1}
            size="small"
            style={{ marginBottom: token.marginLG }}
            labelStyle={{ width: 120 }}
          >
            <Descriptions.Item label="文件名">{selectedFile?.name || '-'}</Descriptions.Item>
            <Descriptions.Item label="总行数">{previewData?.totalRows || 0}</Descriptions.Item>
            <Descriptions.Item label="有效行数">
              <Text style={{ color: token.colorSuccess }}>{validTransactions.length}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="无效行数">
              <Text style={{ color: token.colorError }}>{invalidTransactions.length}</Text>
            </Descriptions.Item>
          </Descriptions>

          <Card
            title="待导入交易记录"
            size="small"
            variant="borderless"
            style={{ marginBottom: token.marginLG }}
          >
            <Table
              dataSource={validTransactions}
              columns={summaryColumns}
              rowKey={(_, index) => index}
              size="small"
              scroll={{ x: 700 }}
              pagination={{
                pageSize: 10,
                showSizeChanger: false,
                showTotal: (total) => `共 ${total} 条`
              }}
            />
          </Card>

          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>
              返回
            </Button>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleConfirmImport}
              loading={loading}
            >
              确认导入
            </Button>
          </div>
        </div>
      )}
    </Spin>
  );

  return (
    <div style={{ margin: '0 auto', marginTop: 24 }}>
      <Card
        variant="borderless"
        title={
          <Space>
            <UploadOutlined />
            <span>CSV 导入</span>
          </Space>
        }
        styles={{ body: { padding: screens.sm ? '20px 24px' : '16px' } }}
      >
        <Steps
          current={currentStep}
          items={stepsItems}
          style={{ marginBottom: token.marginXL }}
          direction={screens.sm ? 'horizontal' : 'vertical'}
        />

        {currentStep === 0 && renderStep0()}
        {currentStep === 1 && renderStep1()}
      </Card>
    </div>
  );
}

export default ImportPage;
