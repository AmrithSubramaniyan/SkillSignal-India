import { useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Calendar,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  BellOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  MailOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface Reminder {
  id: string
  applicationId: string | null
  reminderType: string
  title: string
  dueAt: string
  status: string
  snoozedUntil: string | null
  completedAt: string | null
  notes: string | null
}

interface ApplicationItem {
  id: string
  jobTitle: string
  company: string
  status: string
}

interface FollowUpDraft {
  applicationId: string
  jobTitle: string
  company: string
  recipientHint: string
  subject: string
  body: string
  policyNote: string
}

const REMINDER_TYPES = [
  { value: 'FOLLOW_UP', label: 'Follow-Up' },
  { value: 'ASSESSMENT', label: 'Assessment' },
  { value: 'INTERVIEW', label: 'Interview' },
  { value: 'OFFER', label: 'Offer Decision' },
]

export default function RemindersPage() {
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [overdue, setOverdue] = useState<Reminder[]>([])
  const [applications, setApplications] = useState<ApplicationItem[]>([])
  const [loading, setLoading] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [rescheduleReminderItem, setRescheduleReminderItem] = useState<Reminder | null>(null)
  const [newDueDateTime, setNewDueDateTime] = useState('')

  const [draftModalOpen, setDraftModalOpen] = useState(false)
  const [selectedAppForDraft, setSelectedAppForDraft] = useState<string | null>(null)
  const [followUpDraft, setFollowUpDraft] = useState<FollowUpDraft | null>(null)
  const [draftLoading, setDraftLoading] = useState(false)

  const [form] = Form.useForm()

  const loadData = async () => {
    setLoading(true)
    try {
      const [remRes, ovRes, appRes] = await Promise.all([
        apiClient.get<Reminder[]>('/reminders'),
        apiClient.get<Reminder[]>('/reminders/overdue'),
        apiClient.get<ApplicationItem[]>('/applications'),
      ])
      setReminders(remRes.data)
      setOverdue(ovRes.data)
      setApplications(appRes.data)
    } catch {
      message.error('Failed to load reminders')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  const handleCreate = async (values: {
    reminderType: string
    title: string
    applicationId?: string
    dueDateTime: string
    notes?: string
  }) => {
    try {
      const iso = new Date(values.dueDateTime).toISOString()
      await apiClient.post('/reminders', {
        reminderType: values.reminderType,
        title: values.title,
        applicationId: values.applicationId || null,
        dueAt: iso,
        notes: values.notes || null,
      })
      message.success('Reminder created')
      setCreateOpen(false)
      form.resetFields()
      loadData()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not create reminder')
    }
  }

  const handleSnoozeDays = async (rem: Reminder, days: number) => {
    try {
      const snoozeTo = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString()
      await apiClient.put(`/reminders/${rem.id}/snooze`, { snoozedUntil: snoozeTo })
      message.success(`Snoozed for ${days} day(s)`)
      loadData()
    } catch {
      message.error('Failed to snooze reminder')
    }
  }

  const handleComplete = async (rem: Reminder) => {
    try {
      await apiClient.put(`/reminders/${rem.id}/complete`)
      message.success('Reminder marked complete')
      loadData()
    } catch {
      message.error('Failed to complete reminder')
    }
  }

  const handleDelete = async (rem: Reminder) => {
    try {
      await apiClient.delete(`/reminders/${rem.id}`)
      message.success('Reminder removed')
      loadData()
    } catch {
      message.error('Failed to delete reminder')
    }
  }

  const handleRescheduleSubmit = async () => {
    if (!rescheduleReminderItem || !newDueDateTime) return
    try {
      await apiClient.put(`/reminders/${rescheduleReminderItem.id}/reschedule`, {
        dueAt: new Date(newDueDateTime).toISOString(),
      })
      message.success('Reminder rescheduled')
      setRescheduleReminderItem(null)
      setNewDueDateTime('')
      loadData()
    } catch {
      message.error('Failed to reschedule reminder')
    }
  }

  const handleGenerateDraft = async () => {
    if (!selectedAppForDraft) {
      message.warning('Select an application first')
      return
    }
    setDraftLoading(true)
    try {
      const res = await apiClient.post<FollowUpDraft>('/reminders/follow-up-draft', {
        applicationId: selectedAppForDraft,
      })
      setFollowUpDraft(res.data)
    } catch {
      message.error('Could not generate follow-up email draft')
    } finally {
      setDraftLoading(false)
    }
  }

  const columns = [
    {
      title: 'Type',
      dataIndex: 'reminderType',
      key: 'reminderType',
      render: (t: string) => {
        const color =
          t === 'INTERVIEW'
            ? 'purple'
            : t === 'ASSESSMENT'
            ? 'cyan'
            : t === 'OFFER'
            ? 'green'
            : 'blue'
        return <Tag color={color}>{t}</Tag>
      },
    },
    {
      title: 'Reminder',
      dataIndex: 'title',
      key: 'title',
      render: (t: string, r: Reminder) => (
        <div>
          <Text strong>{t}</Text>
          {r.notes && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {r.notes}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Due At',
      dataIndex: 'dueAt',
      key: 'dueAt',
      render: (d: string) => new Date(d).toLocaleString(),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (st: string) => {
        const color = st === 'COMPLETED' ? 'green' : st === 'SNOOZED' ? 'gold' : 'processing'
        return <Tag color={color}>{st}</Tag>
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, r: Reminder) => (
        <Space wrap>
          {r.status !== 'COMPLETED' && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => handleComplete(r)}
              >
                Complete
              </Button>
              <Button size="small" onClick={() => handleSnoozeDays(r, 1)}>
                Snooze +1d
              </Button>
              <Button size="small" onClick={() => handleSnoozeDays(r, 3)}>
                Snooze +3d
              </Button>
              <Button
                size="small"
                icon={<ClockCircleOutlined />}
                onClick={() => {
                  setRescheduleReminderItem(r)
                  setNewDueDateTime(r.dueAt.slice(0, 16))
                }}
              >
                Reschedule
              </Button>
            </>
          )}
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(r)} />
        </Space>
      ),
    },
  ]

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>
            <CalendarOutlined /> Reminders & Calendar
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Track follow-ups, assessments, interviews, and offers. Silence is never treated as rejection.
          </Paragraph>
        </Col>
        <Col>
          <Space>
            <Button icon={<MailOutlined />} onClick={() => setDraftModalOpen(true)}>
              AI Follow-Up Email Draft
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              New Reminder
            </Button>
          </Space>
        </Col>
      </Row>

      {overdue.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`You have ${overdue.length} due/overdue reminder(s)`}
          description="Overdue follow-up reminders stay actionable until you complete, snooze, or reschedule them. Recruiter silence never marks an application as Rejected."
        />
      )}

      <Card>
        <Tabs
          items={[
            {
              key: 'all',
              label: `All Reminders (${reminders.length})`,
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={reminders}
                  columns={columns}
                  pagination={{ pageSize: 10 }}
                />
              ),
            },
            {
              key: 'overdue',
              label: (
                <span>
                  Overdue / Due Now <Badge count={overdue.length} style={{ marginLeft: 6 }} />
                </span>
              ),
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={overdue}
                  columns={columns}
                  pagination={{ pageSize: 10 }}
                />
              ),
            },
            {
              key: 'calendar',
              label: 'Calendar View',
              children: (
                <Calendar
                  fullscreen={false}
                  cellRender={(current) => {
                    const dayStr = current.format('YYYY-MM-DD')
                    const matching = reminders.filter(
                      (r) => r.dueAt.slice(0, 10) === dayStr && r.status !== 'COMPLETED'
                    )
                    return (
                      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                        {matching.map((m) => (
                          <li key={m.id}>
                            <Badge status="processing" text={m.title} />
                          </li>
                        ))}
                      </ul>
                    )
                  }}
                />
              ),
            },
          ]}
        />
      </Card>

      {/* Create Reminder Modal */}
      <Modal
        title={
          <Space>
            <BellOutlined /> New Reminder
          </Space>
        }
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        okText="Save Reminder"
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          initialValues={{ reminderType: 'FOLLOW_UP' }}
        >
          <Form.Item name="reminderType" label="Reminder Type" rules={[{ required: true }]}>
            <Select options={REMINDER_TYPES} />
          </Form.Item>
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input placeholder="e.g. Follow up with Hiring Manager at FinTech India" />
          </Form.Item>
          <Form.Item name="applicationId" label="Linked Application (Optional)">
            <Select
              allowClear
              placeholder="Select tracked application"
              options={applications.map((a) => ({
                value: a.id,
                label: `${a.jobTitle} @ ${a.company} (${a.status})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="dueDateTime" label="Due Date & Time" rules={[{ required: true }]}>
            <Input type="datetime-local" />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} placeholder="Preparation notes or context..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reschedule Modal */}
      <Modal
        title="Reschedule Reminder"
        open={!!rescheduleReminderItem}
        onCancel={() => setRescheduleReminderItem(null)}
        onOk={handleRescheduleSubmit}
        okText="Reschedule"
      >
        <Paragraph>Select a new date and time for: {rescheduleReminderItem?.title}</Paragraph>
        <Input
          type="datetime-local"
          value={newDueDateTime}
          onChange={(e) => setNewDueDateTime(e.target.value)}
        />
      </Modal>

      {/* AI Follow-Up Email Draft Modal */}
      <Modal
        title={
          <Space>
            <MailOutlined /> AI Follow-Up Email Draft (User Sends Manually)
          </Space>
        }
        open={draftModalOpen}
        onCancel={() => {
          setDraftModalOpen(false)
          setFollowUpDraft(null)
        }}
        footer={[
          <Button
            key="close"
            onClick={() => {
              setDraftModalOpen(false)
              setFollowUpDraft(null)
            }}
          >
            Close
          </Button>,
        ]}
        width={680}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text strong>Select Application:</Text>
            <Space style={{ width: '100%', marginTop: 8 }}>
              <Select
                style={{ width: 420 }}
                placeholder="Choose an application to draft a follow-up for"
                value={selectedAppForDraft}
                onChange={(v) => setSelectedAppForDraft(v)}
                options={applications.map((a) => ({
                  value: a.id,
                  label: `${a.jobTitle} @ ${a.company} (${a.status})`,
                }))}
              />
              <Button type="primary" loading={draftLoading} onClick={handleGenerateDraft}>
                Generate Draft
              </Button>
            </Space>
          </div>

          {followUpDraft && (
            <Card size="small" title={`Draft for ${followUpDraft.jobTitle} @ ${followUpDraft.company}`}>
              <Alert
                type="info"
                showIcon
                message={followUpDraft.policyNote}
                style={{ marginBottom: 12 }}
              />
              <Paragraph>
                <Text strong>Recipient Hint:</Text> {followUpDraft.recipientHint}
              </Paragraph>
              <Paragraph copyable={{ text: followUpDraft.subject }}>
                <Text strong>Subject:</Text> {followUpDraft.subject}
              </Paragraph>
              <Input.TextArea value={followUpDraft.body} rows={8} readOnly />
            </Card>
          )}
        </Space>
      </Modal>
    </div>
  )
}

