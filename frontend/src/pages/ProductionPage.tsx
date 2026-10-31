import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  List,
  Rate,
  Row,
  Select,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { CrownOutlined, MessageOutlined, SafetyOutlined } from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text } = Typography
const { TextArea } = Input

export default function ProductionPage() {
  const [flagsData, setFlagsData] = useState<any>({ flags: [] })
  const [knownIssuesData, setKnownIssuesData] = useState<any>({ knownIssues: [] })
  const [feedbacks, setFeedbacks] = useState<any[]>([])
  const [form] = Form.useForm()

  const loadAll = async () => {
    try {
      const [fRes, kRes, fbRes] = await Promise.all([
        apiClient.get('/api/production/feature-flags'),
        apiClient.get('/api/production/known-issues'),
        apiClient.get('/api/production/feedback'),
      ])
      setFlagsData(fRes.data)
      setKnownIssuesData(kRes.data)
      setFeedbacks(fbRes.data)
      await apiClient.post('/api/production/usage-events', {
        eventName: 'VIEW_PLANS_AND_FEEDBACK_PAGE',
        metadata: { version: 'v1.0.0-lite' },
      })
    } catch {
      message.error('Failed to load Plans, Feedback & Known Issues')
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const submitFeedback = async (values: any) => {
    try {
      await apiClient.post('/api/production/feedback', {
        category: values.category || 'GENERAL',
        rating: values.rating || 5,
        message: values.message,
        pageContext: '/plans-and-feedback',
      })
      form.resetFields()
      message.success('Thank you! Your feedback has been saved.')
      loadAll()
    } catch {
      message.error('Failed to submit feedback')
    }
  }

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Title level={2}>
        <CrownOutlined /> Plans (Feature Flags), Feedback & Known Issues
      </Title>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          knownIssuesData.gateG7Reminder ||
          'GATE G7 REMINDER: Obtain formal legal review (DPDP Act 2023 privacy notice, data retention, and portal terms) before public launch.'
        }
        description={flagsData.note}
      />

      <Card title="Free vs Paid Feature-Flag Table (No Payments Active)" style={{ marginBottom: 16 }}>
        <Table
          size="small"
          rowKey="flagKey"
          pagination={false}
          dataSource={flagsData.flags || []}
          columns={[
            { title: 'Feature', dataIndex: 'displayName' },
            { title: 'Description', dataIndex: 'description' },
            {
              title: 'Free Tier',
              render: (_: any, r: any) => (
                <span>
                  <Tag color={r.freeTierEnabled ? 'green' : 'default'}>
                    {r.freeTierEnabled ? 'Included' : 'Preview Only'}
                  </Tag>{' '}
                  <Text type="secondary">{r.freeTierLimit}</Text>
                </span>
              ),
            },
            {
              title: 'Pro / Paid Tier (Roadmap)',
              render: (_: any, r: any) => (
                <span>
                  <Tag color="blue">{r.paidTierEnabled ? 'Included' : 'Disabled'}</Tag>{' '}
                  <Text strong>{r.paidTierLimit}</Text>
                </span>
              ),
            },
          ]}
        />
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title={<><MessageOutlined /> Submit Product Feedback</>}>
            <Form form={form} layout="vertical" onFinish={submitFeedback} initialValues={{ category: 'GENERAL', rating: 5 }}>
              <Form.Item name="category" label="Category">
                <Select
                  options={[
                    { label: 'General Experience', value: 'GENERAL' },
                    { label: 'Job Match Accuracy', value: 'MATCH_QUALITY' },
                    { label: 'Auto-Apply & Safety Governor', value: 'AUTO_APPLY' },
                    { label: 'Bug Report', value: 'BUG' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="rating" label="Rating">
                <Rate />
              </Form.Item>
              <Form.Item
                name="message"
                label="Your Feedback"
                rules={[{ required: true, message: 'Please enter feedback message' }]}
              >
                <TextArea rows={3} placeholder="Tell us what worked well or what needs improvement..." />
              </Form.Item>
              <Button type="primary" htmlType="submit">
                Submit Feedback
              </Button>
            </Form>
            <List
              style={{ marginTop: 16 }}
              size="small"
              header={<Text strong>Your Submitted Feedback ({feedbacks.length})</Text>}
              dataSource={feedbacks}
              renderItem={(fb: any) => (
                <List.Item>
                  <Tag color="blue">{fb.category}</Tag> <Tag color="gold">{fb.rating}/5</Tag> {fb.message}
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title={<><SafetyOutlined /> Known Issues & ComplianceGates (v1.0.0-lite)</>}>
            <List
              dataSource={knownIssuesData.knownIssues || []}
              renderItem={(item: any) => (
                <List.Item>
                  <div>
                    <Tag color="purple">{item.id}</Tag> <Text strong>{item.title}</Text>{' '}
                    <Tag color="orange">{item.status}</Tag>
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary">{item.detail}</Text>
                    </div>
                  </div>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}

