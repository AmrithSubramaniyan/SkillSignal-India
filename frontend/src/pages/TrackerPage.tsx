import React, { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Select,
  Tag,
  Button,
  Space,
  Typography,
  Modal,
  Timeline,
  Input,
  Alert,
  Spin,
  Empty,
  Popconfirm,
  message,
} from 'antd'
import {
  HistoryOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Paragraph, Text } = Typography
const { TextArea } = Input

export interface ApplicationDto {
  id: string
  jobId: string
  jobTitle: string
  company: string
  location: string
  workMode: string
  resumeVersionId: string | null
  status: string
  applyMode: string
  notes: string | null
  appliedAt: string | null
  followUpDate: string | null
  matchScore: number | null
  createdAt: string
  updatedAt: string
}

interface ApplicationEventDto {
  id: string
  fromStatus: string | null
  toStatus: string
  note: string | null
  createdAt: string
}

const STATUS_OPTIONS = [
  { label: 'Saved', value: 'SAVED', color: 'default' },
  { label: 'Preparing Pack', value: 'PREPARING', color: 'processing' },
  { label: 'Ready to Apply', value: 'READY_TO_APPLY', color: 'cyan' },
  { label: 'Applied (Confirmed)', value: 'APPLIED_CONFIRMED', color: 'blue' },
  { label: 'Needs Verification', value: 'NEEDS_VERIFICATION', color: 'warning' },
  { label: 'Interviewing', value: 'INTERVIEWING', color: 'purple' },
  { label: 'Offer Received', value: 'OFFER', color: 'success' },
  { label: 'Rejected', value: 'REJECTED', color: 'error' },
  { label: 'Withdrawn', value: 'WITHDRAWN', color: 'default' },
]

export function statusColor(status: string): string {
  return STATUS_OPTIONS.find((o) => o.value === status)?.color || 'default'
}

export default function TrackerPage() {
  const [apps, setApps] = useState<ApplicationDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [eventsModalApp, setEventsModalApp] = useState<ApplicationDto | null>(null)
  const [events, setEvents] = useState<ApplicationEventDto[]>([])
  const [loadingEvents, setLoadingEvents] = useState(false)
  const [editingApp, setEditingApp] = useState<ApplicationDto | null>(null)
  const [editNotes, setEditNotes] = useState('')
  const [editFollowUp, setEditFollowUp] = useState('')

  const loadApplications = async () => {
    setLoading(true)
    try {
      const res = await apiClient.get<ApplicationDto[]>('/applications')
      setApps(res.data)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to load application tracker.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadApplications()
  }, [])

  const onStatusChange = async (appId: string, newStatus: string) => {
    try {
      await apiClient.put(`/applications/${appId}`, { status: newStatus })
      message.success(`Application status updated to ${newStatus}`)
      await loadApplications()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to update status.')
    }
  }

  const onOpenHistory = async (app: ApplicationDto) => {
    setEventsModalApp(app)
    setLoadingEvents(true)
    try {
      const res = await apiClient.get<ApplicationEventDto[]>(`/applications/${app.id}/events`)
      setEvents(res.data)
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to load status history.')
    } finally {
      setLoadingEvents(false)
    }
  }

  const onOpenEdit = (app: ApplicationDto) => {
    setEditingApp(app)
    setEditNotes(app.notes || '')
    setEditFollowUp(app.followUpDate || '')
  }

  const onSaveEdit = async () => {
    if (!editingApp) return
    try {
      await apiClient.put(`/applications/${editingApp.id}`, {
        notes: editNotes,
        followUpDate: editFollowUp || null,
      })
      message.success('Notes and follow-up date updated!')
      setEditingApp(null)
      await loadApplications()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to update application.')
    }
  }

  const onDelete = async (appId: string) => {
    try {
      await apiClient.delete(`/applications/${appId}`)
      message.success('Application removed.')
      await loadApplications()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to delete application.')
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading application tracker..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1120, margin: '24px auto', padding: '0 24px' }}>
      <Card>
        <Title level={3} style={{ marginTop: 0 }}>
          Application Tracker & Follow-Ups
        </Title>
        <Paragraph type="secondary">
          Track every application from Saved to Offer. In accordance with SkillSignal safety rules, applications are never marked submitted automatically — uncertain portal states are flagged as <Tag color="warning">NEEDS_VERIFICATION</Tag> for you to verify and resolve.
        </Paragraph>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        {apps.length === 0 ? (
          <Empty description="No applications tracked yet. Go to 'Jobs & Match' and click 'Save to Tracker' on any role." />
        ) : (
          <Table
            rowKey="id"
            dataSource={apps}
            pagination={{ pageSize: 10 }}
            columns={[
              {
                title: 'Role & Company',
                key: 'role',
                render: (_, r) => (
                  <div>
                    <Text strong>{r.jobTitle}</Text>
                    <div>
                      <Text type="secondary">
                        {r.company} {r.location ? `• ${r.location}` : ''} {r.workMode ? `(${r.workMode})` : ''}
                      </Text>
                    </div>
                  </div>
                ),
              },
              {
                title: 'Match Estimate',
                dataIndex: 'matchScore',
                render: (score: number | null) =>
                  score !== null ? (
                    <Tag color={score >= 75 ? 'success' : score >= 50 ? 'warning' : 'error'}>{score}%</Tag>
                  ) : (
                    <Text type="secondary">—</Text>
                  ),
              },
              {
                title: 'Status',
                dataIndex: 'status',
                render: (val: string, r) => (
                  <Space direction="vertical" size={4}>
                    <Select
                      size="small"
                      style={{ width: 185 }}
                      value={val}
                      onChange={(newVal) => onStatusChange(r.id, newVal)}
                      options={STATUS_OPTIONS.map((o) => ({ label: o.label, value: o.value }))}
                    />
                    {val === 'NEEDS_VERIFICATION' && (
                      <Tag icon={<ExclamationCircleOutlined />} color="warning">
                        Verify submission manually
                      </Tag>
                    )}
                  </Space>
                ),
              },
              {
                title: 'Follow-Up Date',
                dataIndex: 'followUpDate',
                render: (date: string | null) => {
                  if (!date) return <Text type="secondary">Not set</Text>
                  const isDue = date <= new Date().toISOString().slice(0, 10)
                  return <Tag color={isDue ? 'volcano' : 'geekblue'}>{date}{isDue ? ' (Due)' : ''}</Tag>
                },
              },
              {
                title: 'Notes',
                dataIndex: 'notes',
                ellipsis: true,
              },
              {
                title: 'Actions',
                key: 'actions',
                render: (_, r) => (
                  <Space>
                    <Button size="small" icon={<EditOutlined />} onClick={() => onOpenEdit(r)}>
                      Edit
                    </Button>
                    <Button size="small" icon={<HistoryOutlined />} onClick={() => onOpenHistory(r)}>
                      History
                    </Button>
                    <Popconfirm title="Remove from tracker?" onConfirm={() => onDelete(r.id)}>
                      <Button size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Card>

      <Modal
        title={`Status History: ${eventsModalApp?.jobTitle || ''}`}
        open={!!eventsModalApp}
        onCancel={() => setEventsModalApp(null)}
        footer={null}
      >
        {loadingEvents ? (
          <Spin />
        ) : (
          <Timeline
            items={events.map((e) => ({
              color: statusColor(e.toStatus) === 'error' ? 'red' : 'blue',
              children: (
                <div>
                  <Text strong>
                    {e.fromStatus ? `${e.fromStatus} → ${e.toStatus}` : `Created as ${e.toStatus}`}
                  </Text>
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {new Date(e.createdAt).toLocaleString()}
                    </Text>
                  </div>
                  {e.note && <div>{e.note}</div>}
                </div>
              ),
            }))}
          />
        )}
      </Modal>

      <Modal
        title={`Update Notes & Follow-Up: ${editingApp?.jobTitle || ''}`}
        open={!!editingApp}
        onCancel={() => setEditingApp(null)}
        onOk={onSaveEdit}
        okText="Save Changes"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text strong>Follow-Up Date (YYYY-MM-DD):</Text>
            <Input
              type="date"
              value={editFollowUp}
              onChange={(e) => setEditFollowUp(e.target.value)}
              style={{ marginTop: 4 }}
            />
          </div>
          <div>
            <Text strong>Application Notes:</Text>
            <TextArea
              rows={4}
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              style={{ marginTop: 4 }}
            />
          </div>
        </Space>
      </Modal>
    </div>
  )
}

