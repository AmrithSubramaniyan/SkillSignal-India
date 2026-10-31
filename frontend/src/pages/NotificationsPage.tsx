import { useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Input,
  List,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  BellOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
  ReloadOutlined,
  SendOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface NotificationItem {
  id: string
  reminderId: string | null
  channel: string
  title: string
  message: string
  read: boolean
  suppressedByQuietHours: boolean
  createdAt: string
}

interface NotificationPreference {
  userId: string
  timeZone: string
  quietHoursStart: string
  quietHoursEnd: string
  dailySummaryEnabled: boolean
  sessionSummaryEmailEnabled: boolean
  includeWhileAwayApplications: boolean
}

interface SessionSummaryRow {
  id: string
  sessionId?: string
  checkedInAt: string
  lastSeenAt?: string
  checkedOutAt: string | null
  checkoutReason: string | null
  timeZone?: string
  companiesAppliedCount: number
  summaryEmailStatus: string
  createdAt?: string
}

const TIME_ZONES = [
  { value: 'Asia/Kolkata', label: 'Asia/Kolkata (IST, UTC+05:30 — Default)' },
  { value: 'UTC', label: 'UTC' },
  { value: 'Europe/London', label: 'Europe/London' },
  { value: 'America/New_York', label: 'America/New_York' },
  { value: 'Asia/Singapore', label: 'Asia/Singapore' },
]

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [sessions, setSessions] = useState<SessionSummaryRow[]>([])
  const [loading, setLoading] = useState(false)
  const [dispatching, setDispatching] = useState(false)
  const [form] = Form.useForm()

  const loadAll = async () => {
    setLoading(true)
    try {
      const [notifRes, prefRes, sessRes] = await Promise.all([
        apiClient.get<NotificationItem[]>('/notifications'),
        apiClient.get<NotificationPreference>('/notification-preferences'),
        apiClient.get<SessionSummaryRow[]>('/sessions'),
      ])
      setNotifications(notifRes.data)
      setSessions(sessRes.data)
      form.setFieldsValue({
        timeZone: prefRes.data.timeZone,
        quietHoursStart: prefRes.data.quietHoursStart,
        quietHoursEnd: prefRes.data.quietHoursEnd,
        dailySummaryEnabled: prefRes.data.dailySummaryEnabled,
        sessionSummaryEmailEnabled: prefRes.data.sessionSummaryEmailEnabled,
        includeWhileAwayApplications: prefRes.data.includeWhileAwayApplications,
      })
    } catch {
      message.error('Failed to load notifications and preferences')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleSavePreferences = async (values: {
    timeZone: string
    quietHoursStart: string
    quietHoursEnd: string
    dailySummaryEnabled: boolean
    sessionSummaryEmailEnabled: boolean
    includeWhileAwayApplications: boolean
  }) => {
    try {
      await apiClient.put('/notification-preferences', values)
      message.success('Notification preferences updated')
      loadAll()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not update preferences')
    }
  }

  const handleResendSessionSummary = async (sessionId: string) => {
    try {
      await apiClient.post(`/sessions/${sessionId}/resend-summary`)
      message.success('Session summary email re-evaluated / resent')
      loadAll()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to resend session summary')
    }
  }

  const handleMarkRead = async (id: string) => {
    try {
      await apiClient.put(`/notifications/${id}/read`)
      loadAll()
    } catch {
      message.error('Could not mark notification read')
    }
  }

  const handleDispatchNow = async () => {
    setDispatching(true)
    try {
      const res = await apiClient.post('/notifications/dispatch', {})
      const d = res.data
      message.success(
        `Evaluated ${d.dueRemindersCount} due reminder(s): ${d.newInAppNotificationsCreated} new notification(s)` +
          (d.suppressedByQuietHours ? ' (Email suppressed during Quiet Hours)' : '')
      )
      loadAll()
    } catch {
      message.error('Failed to evaluate notifications')
    } finally {
      setDispatching(false)
    }
  }

  const unreadCount = notifications.filter((n) => !n.read).length

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>
            <BellOutlined /> Notifications & Quiet Hours{' '}
            <Badge count={unreadCount} style={{ marginLeft: 8 }} />
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            In-app notifications, daily summary email dispatch, sent-log deduplication, and per-user time zone & quiet hours.
          </Paragraph>
        </Col>
        <Col>
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={dispatching}
            onClick={handleDispatchNow}
          >
            Evaluate Due Reminders & Daily Summary
          </Button>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={15}>
          <Card title={`In-App & Summary Notifications (${notifications.length})`} loading={loading}>
            <List
              dataSource={notifications}
              locale={{ emptyText: 'No notifications yet' }}
              renderItem={(item) => (
                <List.Item
                  actions={
                    !item.read
                      ? [
                          <Button
                            key="read"
                            size="small"
                            icon={<CheckOutlined />}
                            onClick={() => handleMarkRead(item.id)}
                          >
                            Mark Read
                          </Button>,
                        ]
                      : [<Tag key="done" color="default">Read</Tag>]
                  }
                >
                  <List.Item.Meta
                    title={
                      <Space wrap>
                        {!item.read && <Badge status="processing" />}
                        <Text strong={!item.read}>{item.title}</Text>
                        <Tag color={item.channel === 'EMAIL' ? 'purple' : 'blue'}>
                          {item.channel}
                        </Tag>
                        {item.suppressedByQuietHours && (
                          <Tag icon={<ClockCircleOutlined />} color="gold">
                            Quiet Hours Active (Email Suppressed)
                          </Tag>
                        )}
                      </Space>
                    }
                    description={
                      <div>
                        <div style={{ whiteSpace: 'pre-wrap' }}>{item.message}</div>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {new Date(item.createdAt).toLocaleString()}
                        </Text>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Card
            title={
              <Space>
                <SettingOutlined /> Time Zone & Quiet Hours
              </Space>
            }
          >
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="Duplicate Prevention & Quiet Hours"
              description="Our sent-log guarantees you never receive duplicate notifications for the same reminder or daily summary. During Quiet Hours in your selected time zone, email summaries are automatically suppressed."
            />
            <Form form={form} layout="vertical" onFinish={handleSavePreferences}>
              <Form.Item name="timeZone" label="Your Time Zone" rules={[{ required: true }]}>
                <Select options={TIME_ZONES} />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item
                    name="quietHoursStart"
                    label="Quiet Hours Start (HH:mm)"
                    rules={[{ required: true }]}
                  >
                    <Input placeholder="22:00" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="quietHoursEnd"
                    label="Quiet Hours End (HH:mm)"
                    rules={[{ required: true }]}
                  >
                    <Input placeholder="08:00" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                name="dailySummaryEnabled"
                label="Daily Summary Email"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item
                name="sessionSummaryEmailEnabled"
                label="Email me a summary of companies I applied to after each session"
                valuePropName="checked"
                extra="Sends ONE email at session check-out if you applied to at least one company. Requires EMAIL_NOTIFICATIONS consent."
              >
                <Switch />
              </Form.Item>
              <Form.Item
                name="includeWhileAwayApplications"
                label="Include applications completed while I was away"
                valuePropName="checked"
                extra="Includes background applications completed between your previous check-out and this session's check-out."
              >
                <Switch />
              </Form.Item>
              <Button type="primary" htmlType="submit" block>
                Save Preferences
              </Button>
            </Form>
          </Card>
        </Col>
      </Row>

      <Card
        style={{ marginTop: 16 }}
        title={
          <Space>
            <HistoryOutlined /> Recent Login Sessions (Check-in / Check-out & Summary Email Status)
          </Space>
        }
        loading={loading}
      >
        <Table
          rowKey={(row) => row.id || row.sessionId || row.checkedInAt}
          size="small"
          pagination={{ pageSize: 8 }}
          dataSource={sessions}
          columns={[
            {
              title: 'Checked In',
              dataIndex: 'checkedInAt',
              render: (v: string) => new Date(v).toLocaleString(),
            },
            {
              title: 'Checked Out',
              dataIndex: 'checkedOutAt',
              render: (v: string | null) => (v ? new Date(v).toLocaleString() : <Tag color="green">ACTIVE</Tag>),
            },
            {
              title: 'Check-out Reason',
              dataIndex: 'checkoutReason',
              render: (v: string | null) => (v ? <Tag>{v}</Tag> : '—'),
            },
            {
              title: 'Companies Applied',
              dataIndex: 'companiesAppliedCount',
              render: (v: number) => <Tag color={v > 0 ? 'blue' : 'default'}>{v}</Tag>,
            },
            {
              title: 'Summary Email Status',
              dataIndex: 'summaryEmailStatus',
              render: (v: string) => {
                const color =
                  v === 'SENT'
                    ? 'green'
                    : v === 'FAILED'
                      ? 'red'
                      : v.startsWith('SKIPPED')
                        ? 'default'
                        : 'processing'
                return <Tag color={color}>{v}</Tag>
              },
            },
            {
              title: 'Actions',
              key: 'actions',
              render: (_: any, row: SessionSummaryRow) =>
                row.checkedOutAt && row.summaryEmailStatus !== 'SENT' ? (
                  <Button
                    size="small"
                    icon={<ReloadOutlined />}
                    onClick={() => handleResendSessionSummary(row.id || row.sessionId || '')}
                  >
                    Re-evaluate / Resend
                  </Button>
                ) : null,
            },
          ]}
        />
      </Card>
    </div>
  )
}

