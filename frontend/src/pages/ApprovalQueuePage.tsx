import { useEffect, useState } from 'react'
import {
  Card,
  Typography,
  Space,
  Button,
  Tag,
  Alert,
  Row,
  Col,
  List,
  Checkbox,
  Modal,
  Input,
  Drawer,
  message,
  Select,
  Badge,
  Divider,
} from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  StepForwardOutlined,
  EditOutlined,
  MailOutlined,
  InfoCircleOutlined,
  SafetyOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { apiClient as api } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface CheckItem {
  checkNumber: number
  name: string
  passed: boolean
  status: string
  detail: string
}

interface SubmissionItem {
  id: string
  jobId: string
  jobTitle: string
  company: string
  location?: string
  source?: string
  sourceUrl?: string
  packId?: string
  applicationId?: string
  state: string
  allowedNextStates: string[]
  idempotencyKey: string
  ruleDecision?: {
    eligible: boolean
    decision: string
    matchedSkills: string[]
    unmatchedSkills: string[]
    matchedSkillsCount: number
    totalJobRequiredSkillsCount: number
    requiredSkillCoveragePercent: number
    nineChecks: CheckItem[]
    whyAppliedOrSkipped: string
  }
  whyAppliedOrSkipped: string
  answersUsed: Array<{
    questionText: string
    category: string
    approvedAnswer: string | null
    requiresUserPause: boolean
  }>
  expiresAt?: string
  expired: boolean
  updatedAt: string
}

export default function ApprovalQueuePage() {
  const [items, setItems] = useState<SubmissionItem[]>([])
  const [jobs, setJobs] = useState<Array<{ id: string; title: string; company: string }>>([])
  const [selectedJobToPrep, setSelectedJobToPrep] = useState<string>('')
  const [loading, setLoading] = useState(false)

  // Batch approve selection & exceptions
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [exceptionIds, setExceptionIds] = useState<string[]>([])

  // Edit answers modal
  const [editingItem, setEditingItem] = useState<SubmissionItem | null>(null)
  const [editedAnswers, setEditedAnswers] = useState<Record<string, string>>({})
  const [saveEditedToLibrary, setSaveEditedToLibrary] = useState(true)

  // Why drawer
  const [whyItem, setWhyItem] = useState<SubmissionItem | null>(null)

  // Resolve Needs Verification modal
  const [resolveItem, setResolveItem] = useState<SubmissionItem | null>(null)
  const [resolveChoice, setResolveChoice] = useState<'SUBMITTED_CONFIRMED' | 'FAILED'>('SUBMITTED_CONFIRMED')
  const [resolveNote, setResolveNote] = useState('')

  // Single-use email approval token redeem
  const [tokenInput, setTokenInput] = useState('')

  const loadQueueAndJobs = async () => {
    setLoading(true)
    try {
      const [qRes, jRes] = await Promise.all([
        api.get<SubmissionItem[]>('/approval-queue'),
        api.get<Array<{ id: string; title: string; company: string }>>('/jobs'),
      ])
      setItems(qRes.data)
      setJobs(jRes.data)
    } catch {
      message.error('Failed to load Approval Queue')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadQueueAndJobs()
  }, [])

  const handlePrepareJob = async () => {
    if (!selectedJobToPrep) {
      message.warning('Select a job to prepare for the Approval Queue')
      return
    }
    try {
      await api.post('/submissions/prepare', {
        jobId: selectedJobToPrep,
        idempotencyKey: `QUEUE_PREP_${selectedJobToPrep}`,
      })
      message.success('Prepared application pack and added to Approval Queue')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to prepare submission')
    }
  }

  const handleApprove = async (id: string) => {
    try {
      await api.post(`/approval-queue/${id}/approve`, {
        note: 'Approved by user in Approval Queue (leads to user-submits path)',
      })
      message.success('Approved! Added to tracker as Ready to Apply (user-submits path).')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Approval failed')
    }
  }

  const handleReject = async (id: string) => {
    try {
      await api.post(`/approval-queue/${id}/reject`, { note: 'Rejected in Approval Queue' })
      message.info('Rejected item in Approval Queue')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Reject failed')
    }
  }

  const handleSkip = async (id: string) => {
    try {
      await api.post(`/approval-queue/${id}/skip`, { note: 'Skipped by user in Approval Queue' })
      message.info('Skipped item (moved to Blocked-Manual Action)')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Skip failed')
    }
  }

  const handleBatchApprove = async () => {
    if (selectedIds.length === 0) {
      message.warning('Select at least one queue item for batch approval')
      return
    }
    try {
      const res = await api.post('/approval-queue/batch-approve', {
        submissionIds: selectedIds,
        exceptionIds,
        note: 'Batch approved with exceptions',
      })
      message.success(
        `Batch approved ${res.data.approvedCount} item(s) (${res.data.skippedExceptionCount} exception(s) excluded)`
      )
      setSelectedIds([])
      setExceptionIds([])
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Batch approval failed')
    }
  }

  const handleSendApprovalLink = async (id: string) => {
    try {
      const res = await api.post(`/approval-queue/${id}/send-approval-link`)
      setTokenInput(res.data.approvalToken)
      message.success('Single-use expiring approval link sent via email! Token pre-filled for quick test.')
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to generate approval link')
    }
  }

  const handleRedeemToken = async () => {
    if (!tokenInput.trim()) {
      message.warning('Enter single-use approval token')
      return
    }
    try {
      await api.post('/approval-queue/approve-by-token', { token: tokenInput.trim() })
      message.success('Redeemed single-use email approval link! Item approved.')
      setTokenInput('')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Invalid, used, or expired token')
    }
  }

  const handleSaveEditedAnswers = async () => {
    if (!editingItem) return
    try {
      await api.put(`/approval-queue/${editingItem.id}/answers`, {
        answers: editedAnswers,
        saveToAnswerLibrary: saveEditedToLibrary,
      })
      message.success('Updated answers in pack and transitioned to EDITED!')
      setEditingItem(null)
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to update answers')
    }
  }

  const handleResolveNeedsVerification = async () => {
    if (!resolveItem) return
    try {
      await api.post(`/submissions/${resolveItem.id}/resolve`, {
        resolution: resolveChoice,
        note: resolveNote || `User resolved Needs Verification as ${resolveChoice}`,
      })
      message.success(`Resolved Needs Verification as ${resolveChoice}`)
      setResolveItem(null)
      setResolveNote('')
      loadQueueAndJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to resolve')
    }
  }

  const stateColor = (state: string) => {
    switch (state) {
      case 'APPROVED':
      case 'SUBMITTED_CONFIRMED':
        return 'green'
      case 'AWAITING_APPROVAL':
      case 'EDITED':
        return 'gold'
      case 'NEEDS_VERIFICATION':
        return 'volcano'
      case 'EXPIRED':
      case 'REJECTED':
      case 'FAILED':
        return 'red'
      default:
        return 'blue'
    }
  }

  return (
    <div style={{ maxWidth: 1150, margin: '24px auto', padding: '0 12px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Row justify="space-between" align="middle" gutter={[16, 16]}>
            <Col xs={24} md={14}>
              <Title level={3} style={{ margin: 0 }}>
                <SafetyOutlined /> Mode 2: Approval & Review Queue
              </Title>
              <Text type="secondary">
                Mobile-friendly Review Queue. Every failure of the 9 eligibility checks routes here and never auto-submits.
                In Sprint 4, approving an item prepares it on the user-submits (Assisted Apply) path.
              </Text>
            </Col>
            <Col xs={24} md={10}>
              <Space wrap style={{ width: '100%', justifyContent: 'flex-end' }}>
                <Select
                  style={{ minWidth: 220 }}
                  placeholder="Select job to prepare..."
                  value={selectedJobToPrep || undefined}
                  onChange={(v) => setSelectedJobToPrep(v)}
                  options={jobs.map((j) => ({ value: j.id, label: `${j.title} @ ${j.company}` }))}
                />
                <Button type="primary" icon={<PlusOutlined />} onClick={handlePrepareJob}>
                  Prepare into Queue
                </Button>
              </Space>
            </Col>
          </Row>

          <Divider style={{ margin: '16px 0' }} />

          <Row justify="space-between" align="middle" gutter={[12, 12]}>
            <Col xs={24} md={12}>
              <Space wrap>
                <Button
                  type="primary"
                  icon={<CheckOutlined />}
                  disabled={selectedIds.length === 0}
                  onClick={handleBatchApprove}
                >
                  Batch Approve Selected ({selectedIds.length})
                  {exceptionIds.length > 0 ? ` [Excluding ${exceptionIds.length}]` : ''}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setSelectedIds(
                      items
                        .filter((i) => i.state === 'AWAITING_APPROVAL' || i.state === 'EDITED')
                        .map((i) => i.id)
                    )
                  }}
                >
                  Select All Awaiting
                </Button>
              </Space>
            </Col>
            <Col xs={24} md={12}>
              <Space wrap style={{ width: '100%', justifyContent: 'flex-end' }}>
                <Input
                  style={{ width: 240 }}
                  placeholder="Paste single-use email token..."
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                />
                <Button icon={<MailOutlined />} onClick={handleRedeemToken}>
                  Redeem Email Approval Link
                </Button>
              </Space>
            </Col>
          </Row>
        </Card>

        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: 'No submissions in Approval Queue yet. Select a job above and click "Prepare into Queue".' }}
          renderItem={(item) => {
            const isSelected = selectedIds.includes(item.id)
            const isException = exceptionIds.includes(item.id)
            return (
              <Card
                key={item.id}
                style={{ marginBottom: 16 }}
                title={
                  <Space wrap>
                    <Checkbox
                      checked={isSelected}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedIds([...selectedIds, item.id])
                        } else {
                          setSelectedIds(selectedIds.filter((id) => id !== item.id))
                        }
                      }}
                    >
                      Select
                    </Checkbox>
                    {isSelected && (
                      <Checkbox
                        checked={isException}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setExceptionIds([...exceptionIds, item.id])
                          } else {
                            setExceptionIds(exceptionIds.filter((id) => id !== item.id))
                          }
                        }}
                      >
                        <Text type="danger">Batch Exception (Skip in Batch)</Text>
                      </Checkbox>
                    )}
                    <Text strong style={{ fontSize: 16 }}>
                      {item.jobTitle} — {item.company}
                    </Text>
                    <Tag color={stateColor(item.state)}>{item.state}</Tag>
                    {item.ruleDecision && (
                      <Badge
                        status={item.ruleDecision.eligible ? 'success' : 'warning'}
                        text={`Matched ${item.ruleDecision.matchedSkillsCount}/${item.ruleDecision.totalJobRequiredSkillsCount} skills (${item.ruleDecision.requiredSkillCoveragePercent}%)`}
                      />
                    )}
                  </Space>
                }
                extra={
                  <Space wrap>
                    <Button
                      size="small"
                      icon={<InfoCircleOutlined />}
                      onClick={() => setWhyItem(item)}
                    >
                      Why Applied / Skipped
                    </Button>
                    <Button
                      size="small"
                      icon={<MailOutlined />}
                      onClick={() => handleSendApprovalLink(item.id)}
                    >
                      Email Link
                    </Button>
                  </Space>
                }
              >
                <Paragraph style={{ marginBottom: 12 }}>
                  <Text strong>Why Decision: </Text>
                  <Text>{item.whyAppliedOrSkipped}</Text>
                </Paragraph>

                {item.state === 'NEEDS_VERIFICATION' && (
                  <Alert
                    style={{ marginBottom: 12 }}
                    type="error"
                    showIcon
                    icon={<ExclamationCircleOutlined />}
                    message="Needs Verification — Automatic retry is strictly blocked"
                    description="SkillSignal never retries an uncertain submission. Please verify whether you received a confirmation email and resolve below."
                    action={
                      <Button
                        size="small"
                        danger
                        type="primary"
                        onClick={() => {
                          setResolveItem(item)
                          setResolveChoice('SUBMITTED_CONFIRMED')
                          setResolveNote('')
                        }}
                      >
                        Resolve Now
                      </Button>
                    }
                  />
                )}

                <Space wrap>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    disabled={
                      item.state === 'APPROVED' ||
                      item.state === 'SUBMITTED_CONFIRMED' ||
                      item.state === 'EXPIRED' ||
                      item.state === 'NEEDS_VERIFICATION'
                    }
                    onClick={() => handleApprove(item.id)}
                  >
                    Approve (User-Submits Path)
                  </Button>
                  <Button
                    icon={<EditOutlined />}
                    onClick={() => {
                      const initMap: Record<string, string> = {}
                      item.answersUsed.forEach((a) => {
                        initMap[a.questionText] = a.approvedAnswer || ''
                      })
                      if (Object.keys(initMap).length === 0) {
                        initMap['What is your current city?'] = ''
                      }
                      setEditedAnswers(initMap)
                      setEditingItem(item)
                    }}
                  >
                    Edit Answers
                  </Button>
                  <Button icon={<StepForwardOutlined />} onClick={() => handleSkip(item.id)}>
                    Skip
                  </Button>
                  <Button danger icon={<CloseOutlined />} onClick={() => handleReject(item.id)}>
                    Reject
                  </Button>
                </Space>
              </Card>
            )
          }}
        />
      </Space>

      {/* Edit Answers Modal */}
      <Modal
        title={`Edit Screening Answers — ${editingItem?.jobTitle || ''}`}
        open={!!editingItem}
        onCancel={() => setEditingItem(null)}
        onOk={handleSaveEditedAnswers}
        okText="Save Answers & Mark EDITED"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          {Object.entries(editedAnswers).map(([q, ans]) => (
            <div key={q}>
              <Text strong>{q}</Text>
              <Input
                value={ans}
                onChange={(e) => setEditedAnswers({ ...editedAnswers, [q]: e.target.value })}
                placeholder="Enter approved answer..."
              />
            </div>
          ))}
          <Checkbox
            checked={saveEditedToLibrary}
            onChange={(e) => setSaveEditedToLibrary(e.target.checked)}
          >
            Also save these answers to my reusable Answer Library
          </Checkbox>
        </Space>
      </Modal>

      {/* Resolve Needs Verification Modal */}
      <Modal
        title="Resolve 'Needs Verification' Submission"
        open={!!resolveItem}
        onCancel={() => setResolveItem(null)}
        onOk={handleResolveNeedsVerification}
        okText="Confirm Resolution"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            message="Golden Rule: Uncertain submissions are never auto-retried. Choose the verified outcome below."
          />
          <Select
            style={{ width: '100%' }}
            value={resolveChoice}
            onChange={(v) => setResolveChoice(v)}
            options={[
              {
                value: 'SUBMITTED_CONFIRMED',
                label: 'SUBMITTED_CONFIRMED — Application was received (sets USER_CONFIRMED & schedules follow-up)',
              },
              {
                value: 'FAILED',
                label: 'FAILED — Application was not received (marks Submission Failed)',
              },
            ]}
          />
          <Input.TextArea
            rows={3}
            placeholder="Resolution note (e.g., confirmed via ATS email receipt)..."
            value={resolveNote}
            onChange={(e) => setResolveNote(e.target.value)}
          />
        </Space>
      </Modal>

      {/* Why Applied / Why Skipped Drawer */}
      <Drawer
        title={`Why Applied / Why Skipped — ${whyItem?.jobTitle || ''}`}
        width={620}
        open={!!whyItem}
        onClose={() => setWhyItem(null)}
      >
        {whyItem && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Alert
              type={whyItem.ruleDecision?.eligible ? 'success' : 'warning'}
              showIcon
              message={whyItem.ruleDecision?.decision || whyItem.state}
              description={whyItem.whyAppliedOrSkipped}
            />
            {whyItem.ruleDecision && (
              <>
                <Card size="small" title="Skill Coverage Breakdown">
                  <Paragraph>
                    <Text strong>Matched Verified Skills ({whyItem.ruleDecision.matchedSkillsCount}): </Text>
                    {whyItem.ruleDecision.matchedSkills.length > 0
                      ? whyItem.ruleDecision.matchedSkills.map((s) => (
                          <Tag color="green" key={s}>
                            {s}
                          </Tag>
                        ))
                      : 'None'}
                  </Paragraph>
                  <Paragraph>
                    <Text strong>Unmatched Job Skills: </Text>
                    {whyItem.ruleDecision.unmatchedSkills.length > 0
                      ? whyItem.ruleDecision.unmatchedSkills.map((s) => (
                          <Tag color="volcano" key={s}>
                            {s}
                          </Tag>
                        ))
                      : 'None'}
                  </Paragraph>
                </Card>
                <List
                  header={<Text strong>The 9 Eligibility Checks</Text>}
                  bordered
                  size="small"
                  dataSource={whyItem.ruleDecision.nineChecks}
                  renderItem={(chk) => (
                    <List.Item>
                      <Space direction="vertical" size={2} style={{ width: '100%' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                          <Text strong>
                            #{chk.checkNumber}. {chk.name}
                          </Text>
                          <Tag color={chk.passed ? 'green' : 'gold'}>{chk.status}</Tag>
                        </div>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {chk.detail}
                        </Text>
                      </Space>
                    </List.Item>
                  )}
                />
              </>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  )
}
