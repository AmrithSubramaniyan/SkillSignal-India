import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Select,
  Tag,
  Button,
  Space,
  Typography,
  Modal,
  Drawer,
  Timeline,
  Input,
  Switch,
  Alert,
  Spin,
  Empty,
  Row,
  Col,
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
  source: string | null
  applicationUrl: string | null
  resumeVersionId: string | null
  status: string
  subState: string | null
  applyMode: string
  submissionMethod: string
  confirmationType: string
  notes: string | null
  recruiterContacts: string | null
  appliedAt: string | null
  assessmentDate: string | null
  interviewDate: string | null
  nextAction: string | null
  followUpDate: string | null
  matchScore: number | null
  allowedNextStatuses: string[]
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
  { label: 'Interested', value: 'INTERESTED', color: 'default' },
  { label: 'Saved', value: 'SAVED', color: 'default' },
  { label: 'Ready to Apply', value: 'READY_TO_APPLY', color: 'cyan' },
  { label: 'Awaiting Approval', value: 'AWAITING_APPROVAL', color: 'gold' },
  { label: 'Applied', value: 'APPLIED', color: 'blue' },
  { label: 'Applied (Confirmed)', value: 'APPLIED_CONFIRMED', color: 'blue' },
  { label: 'Submission Failed', value: 'SUBMISSION_FAILED', color: 'error' },
  { label: 'Needs Verification', value: 'NEEDS_VERIFICATION', color: 'warning' },
  { label: 'Assessment', value: 'ASSESSMENT', color: 'geekblue' },
  { label: 'Interview', value: 'INTERVIEW', color: 'purple' },
  { label: 'On Hold', value: 'ON_HOLD', color: 'orange' },
  { label: 'Offer', value: 'OFFER', color: 'success' },
  { label: 'Rejected', value: 'REJECTED', color: 'error' },
  { label: 'Withdrawn', value: 'WITHDRAWN', color: 'default' },
  { label: 'Closed', value: 'CLOSED', color: 'default' },
]

export function statusColor(status: string): string {
  return STATUS_OPTIONS.find((o) => o.value === status)?.color || 'default'
}

export default function TrackerPage() {
  const [apps, setApps] = useState<ApplicationDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [filterCompany, setFilterCompany] = useState<string>('')
  const [filterNeedsVerification, setFilterNeedsVerification] = useState(false)

  const [historyDrawerApp, setHistoryDrawerApp] = useState<ApplicationDto | null>(null)
  const [events, setEvents] = useState<ApplicationEventDto[]>([])
  const [loadingEvents, setLoadingEvents] = useState(false)

  const [editingApp, setEditingApp] = useState<ApplicationDto | null>(null)
  const [editNotes, setEditNotes] = useState('')
  const [editRecruiter, setEditRecruiter] = useState('')
  const [editFollowUp, setEditFollowUp] = useState('')
  const [editAssessmentDate, setEditAssessmentDate] = useState('')
  const [editInterviewDate, setEditInterviewDate] = useState('')
  const [editNextAction, setEditNextAction] = useState('')
  const [editConfirmationType, setEditConfirmationType] = useState('USER_CONFIRMED')

  const loadApplications = async () => {
    setLoading(true)
    try {
      const params: Record<string, any> = {}
      if (filterStatus) params.status = filterStatus
      if (filterCompany.trim()) params.company = filterCompany.trim()
      if (filterNeedsVerification) params.needsVerificationOnly = true
      const res = await apiClient.get<ApplicationDto[]>('/applications', { params })
      setApps(res.data)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to load application tracker.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadApplications()
  }, [filterStatus, filterCompany, filterNeedsVerification])

  const onStatusChange = async (app: ApplicationDto, newStatus: string) => {
    try {
      await apiClient.put(`/applications/${app.id}`, {
        status: newStatus,
        confirmationType:
          newStatus === 'APPLIED' || newStatus === 'APPLIED_CONFIRMED'
            ? 'USER_CONFIRMED'
            : app.confirmationType,
      })
      message.success(`Application status updated to ${newStatus}`)
      await loadApplications()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Illegal status transition blocked by state machine.')
    }
  }

  const onOpenHistory = async (app: ApplicationDto) => {
    setHistoryDrawerApp(app)
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
    setEditRecruiter(app.recruiterContacts || '')
    setEditFollowUp(app.followUpDate || '')
    setEditAssessmentDate(app.assessmentDate || '')
    setEditInterviewDate(app.interviewDate || '')
    setEditNextAction(app.nextAction || '')
    setEditConfirmationType(app.confirmationType || 'UNCONFIRMED')
  }

  const onSaveEdit = async () => {
    if (!editingApp) return
    try {
      await apiClient.put(`/applications/${editingApp.id}`, {
        notes: editNotes,
        recruiterContacts: editRecruiter,
        followUpDate: editFollowUp || null,
        assessmentDate: editAssessmentDate || null,
        interviewDate: editInterviewDate || null,
        nextAction: editNextAction || null,
        confirmationType: editConfirmationType,
      })
      message.success('Application details updated!')
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

  return (
    <div style={{ maxWidth: 1220, margin: '24px auto', padding: '0 24px' }}>
      <Card>
        <Title level={3} style={{ marginTop: 0 }}>
          My Applications (Full State-Machine Tracker)
        </Title>
        <Paragraph type="secondary">
          Legal status transitions are strictly enforced by the backend state machine. Applications are never marked submitted automatically — unconfirmed submissions enter sub-state <Tag color="warning">NEEDS_VERIFICATION</Tag> until you confirm.
        </Paragraph>

        <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={8}>
            <Select
              allowClear
              style={{ width: '100%' }}
              placeholder="Filter by Status"
              value={filterStatus || undefined}
              onChange={(val) => setFilterStatus(val || '')}
              options={STATUS_OPTIONS.map((o) => ({ label: o.label, value: o.value }))}
            />
          </Col>
          <Col xs={24} sm={8}>
            <Input
              allowClear
              placeholder="Filter by Company..."
              value={filterCompany}
              onChange={(e) => setFilterCompany(e.target.value)}
            />
          </Col>
          <Col xs={24} sm={8} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Switch
              size="small"
              checked={filterNeedsVerification}
              onChange={(checked) => setFilterNeedsVerification(checked)}
            />
            <Text>Needs Verification Only</Text>
          </Col>
        </Row>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin size="large" />
          </div>
        ) : apps.length === 0 ? (
          <Empty description="No matching applications in tracker." />
        ) : (
          <Table
            rowKey="id"
            dataSource={apps}
            pagination={{ pageSize: 10 }}
            columns={[
              {
                title: 'Role, Company & Source',
                key: 'role',
                render: (_, r) => (
                  <div>
                    <Text strong>{r.jobTitle}</Text>
                    <div>
                      <Text type="secondary">
                        {r.company} {r.location ? `• ${r.location}` : ''} ({r.source || 'manual'})
                      </Text>
                    </div>
                  </div>
                ),
              },
              {
                title: 'Status & Confirmation',
                dataIndex: 'status',
                render: (val: string, r) => {
                  const allowedSet = new Set([val, ...(r.allowedNextStatuses || [])])
                  const filteredOptions = STATUS_OPTIONS.filter((o) => allowedSet.has(o.value))
                  return (
                    <Space direction="vertical" size={4}>
                      <Select
                        size="small"
                        style={{ width: 190 }}
                        value={val}
                        onChange={(newVal) => onStatusChange(r, newVal)}
                        options={filteredOptions.map((o) => ({ label: o.label, value: o.value }))}
                      />
                      <Space size={4} wrap>
                        <Tag color={r.confirmationType === 'UNCONFIRMED' ? 'default' : 'green'}>
                          {r.confirmationType}
                        </Tag>
                        {(val === 'NEEDS_VERIFICATION' || r.subState === 'NEEDS_VERIFICATION') && (
                          <Tag icon={<ExclamationCircleOutlined />} color="warning">
                            Needs Verification
                          </Tag>
                        )}
                      </Space>
                    </Space>
                  )
                },
              },
              {
                title: 'Key Dates & Next Action',
                key: 'dates',
                render: (_, r) => (
                  <Space direction="vertical" size={2}>
                    {r.followUpDate && <Tag color="geekblue">Follow-Up: {r.followUpDate}</Tag>}
                    {r.assessmentDate && <Tag color="cyan">Assessment: {r.assessmentDate}</Tag>}
                    {r.interviewDate && <Tag color="purple">Interview: {r.interviewDate}</Tag>}
                    {r.nextAction && <Text type="secondary" style={{ fontSize: 12 }}>Next: {r.nextAction}</Text>}
                  </Space>
                ),
              },
              {
                title: 'Recruiter & Notes',
                key: 'notes',
                render: (_, r) => (
                  <div>
                    {r.recruiterContacts && (
                      <div>
                        <Text strong style={{ fontSize: 12 }}>Recruiter: </Text>
                        <Text style={{ fontSize: 12 }}>{r.recruiterContacts}</Text>
                      </div>
                    )}
                    <Text type="secondary" style={{ fontSize: 12 }}>{r.notes || '—'}</Text>
                  </div>
                ),
              },
              {
                title: 'Actions',
                key: 'actions',
                render: (_, r) => (
                  <Space>
                    <Button size="small" icon={<EditOutlined />} onClick={() => onOpenEdit(r)}>
                      Details
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

      <Drawer
        title={`Status History: ${historyDrawerApp?.jobTitle || ''} @ ${historyDrawerApp?.company || ''}`}
        open={!!historyDrawerApp}
        onClose={() => setHistoryDrawerApp(null)}
        width={420}
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
      </Drawer>

      <Modal
        title={`Edit Application Details: ${editingApp?.jobTitle || ''}`}
        open={!!editingApp}
        onCancel={() => setEditingApp(null)}
        onOk={onSaveEdit}
        okText="Save Changes"
        width={620}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Row gutter={12}>
            <Col span={12}>
              <Text strong>Confirmation Type:</Text>
              <Select
                style={{ width: '100%', marginTop: 4 }}
                value={editConfirmationType}
                onChange={(val) => setEditConfirmationType(val)}
                options={[
                  { label: 'USER_CONFIRMED', value: 'USER_CONFIRMED' },
                  { label: 'EXTERNALLY_CONFIRMED', value: 'EXTERNALLY_CONFIRMED' },
                  { label: 'UNCONFIRMED (Needs Verification)', value: 'UNCONFIRMED' },
                ]}
              />
            </Col>
            <Col span={12}>
              <Text strong>Next Action:</Text>
              <Input
                value={editNextAction}
                onChange={(e) => setEditNextAction(e.target.value)}
                placeholder="e.g., Send follow-up note"
                style={{ marginTop: 4 }}
              />
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Text strong>Follow-Up Date:</Text>
              <Input
                type="date"
                value={editFollowUp}
                onChange={(e) => setEditFollowUp(e.target.value)}
                style={{ marginTop: 4 }}
              />
            </Col>
            <Col span={8}>
              <Text strong>Assessment Date:</Text>
              <Input
                type="date"
                value={editAssessmentDate}
                onChange={(e) => setEditAssessmentDate(e.target.value)}
                style={{ marginTop: 4 }}
              />
            </Col>
            <Col span={8}>
              <Text strong>Interview Date:</Text>
              <Input
                type="date"
                value={editInterviewDate}
                onChange={(e) => setEditInterviewDate(e.target.value)}
                style={{ marginTop: 4 }}
              />
            </Col>
          </Row>
          <div>
            <Text strong>Recruiter Contacts (User-Supplied):</Text>
            <Input
              value={editRecruiter}
              onChange={(e) => setEditRecruiter(e.target.value)}
              placeholder="Name, email, or LinkedIn URL provided by you"
              style={{ marginTop: 4 }}
            />
          </div>
          <div>
            <Text strong>Application Notes:</Text>
            <TextArea
              rows={3}
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
