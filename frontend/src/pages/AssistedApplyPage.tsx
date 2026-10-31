import { useEffect, useState } from 'react'
import {
  Card,
  Typography,
  Space,
  Button,
  Select,
  Tag,
  Alert,
  Row,
  Col,
  Progress,
  List,
  message,
  Divider,
  Descriptions,
  Input,
  Modal,
} from 'antd'
import {
  CopyOutlined,
  LinkOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
  BookOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { apiClient as api } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface JobItem {
  id: string
  title: string
  company: string
  location?: string
  source?: string
  sourceUrl?: string
}

interface QuestionResolution {
  questionText: string
  normalizedQuestion: string
  category: string
  approvedAnswer: string | null
  confidence: number
  source: string
  autoFillAllowed: boolean
  requiresUserPause: boolean
  offerToSaveNewAnswer: boolean
  status: string
  reason: string
}

interface ApplicationPackDto {
  id: string
  jobId: string
  resumeVersionId?: string
  resumeFilename?: string
  resumeHash?: string
  jobSnapshot: Record<string, any>
  jobSnapshotHash: string
  notesThreeTones: {
    formal: string
    concise: string
    enthusiastic: string
    customOverride?: string
  }
  answers: QuestionResolution[]
  truthfulnessReport: {
    passed: boolean
    verifiedClaims: string[]
    unsupportedClaims: string[]
  }
  truthfulnessPassed: boolean
  readinessScore: number
  readinessBreakdown: {
    matchComponent: number
    answerCoverageComponent: number
    resumeFitComponent: number
    freshnessComponent: number
    totalReadinessScore: number
    userThreshold: number
    routesToReviewQueue: boolean
    routingReasons: string[]
  }
  routesToReviewQueue: boolean
}

export default function AssistedApplyPage() {
  const [jobs, setJobs] = useState<JobItem[]>([])
  const [selectedJobId, setSelectedJobId] = useState<string>('')
  const [pack, setPack] = useState<ApplicationPackDto | null>(null)
  const [submissionId, setSubmissionId] = useState<string | null>(null)
  const [submissionState, setSubmissionState] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  // Answer Library modal state
  const [answerModalOpen, setAnswerModalOpen] = useState(false)
  const [modalQuestion, setModalQuestion] = useState('')
  const [modalCategory, setModalCategory] = useState('FACTUAL')
  const [modalAnswer, setModalAnswer] = useState('')

  const loadJobs = async () => {
    try {
      const res = await api.get<JobItem[]>('/jobs')
      setJobs(res.data)
      if (res.data.length > 0 && !selectedJobId) {
        setSelectedJobId(res.data[0].id)
      }
    } catch {
      message.error('Failed to load jobs')
    }
  }

  useEffect(() => {
    loadJobs()
  }, [])

  const loadPackAndPrepare = async (jobId: string) => {
    if (!jobId) return
    setLoading(true)
    try {
      const [packRes, subRes] = await Promise.all([
        api.get<ApplicationPackDto>(`/applications/packs/${jobId}`),
        api.post('/submissions/prepare', {
          jobId,
          idempotencyKey: `ASSISTED_PREP_${jobId}`,
        }),
      ])
      setPack(packRes.data)
      setSubmissionId(subRes.data.id)
      setSubmissionState(subRes.data.state)
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to load application pack')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (selectedJobId) {
      loadPackAndPrepare(selectedJobId)
    }
  }, [selectedJobId])

  const copyText = (label: string, text: string) => {
    navigator.clipboard.writeText(text)
    message.success(`Copied ${label} to clipboard`)
  }

  const handleConfirmApplied = async () => {
    if (!submissionId) return
    setLoading(true)
    try {
      const res = await api.post(`/approval-queue/${submissionId}/confirm-assisted-applied`, {
        note: 'User clicked "I applied" on Assisted Apply screen (USER_CONFIRMED)',
      })
      setSubmissionState(res.data.state)
      message.success(
        'Marked as Applied (USER_CONFIRMED)! A 7-day Follow-Up Reminder and email draft have been auto-created.'
      )
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to confirm application')
    } finally {
      setLoading(false)
    }
  }

  const handleSaveToAnswerLibrary = async () => {
    if (!modalQuestion.trim() || !modalAnswer.trim()) {
      message.warning('Question and Approved Answer are required')
      return
    }
    try {
      await api.post('/answers', {
        questionText: modalQuestion.trim(),
        category: modalCategory,
        approvedAnswer: modalAnswer.trim(),
        confidence: 1.0,
      })
      message.success('Saved to Answer Library!')
      setAnswerModalOpen(false)
      if (selectedJobId) {
        loadPackAndPrepare(selectedJobId)
      }
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to save answer')
    }
  }

  const currentJob = jobs.find((j) => j.id === selectedJobId)

  return (
    <div style={{ maxWidth: 1150, margin: '24px auto', padding: '0 16px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Row justify="space-between" align="middle" gutter={[16, 16]}>
            <Col xs={24} md={14}>
              <Title level={3} style={{ margin: 0 }}>
                <SafetyCertificateOutlined /> Mode 1: Assisted Apply Pack Preview
              </Title>
              <Text type="secondary">
                Review your truthfulness-verified pack, copy grounded notes & answers, open the original job page, and click
                &ldquo;I applied&rdquo; to record USER_CONFIRMED status and auto-schedule your follow-up reminder.
              </Text>
            </Col>
            <Col xs={24} md={10}>
              <Space style={{ width: '100%', justifyContent: 'flex-end' }} wrap>
                <Select
                  style={{ minWidth: 260 }}
                  placeholder="Select a stored job..."
                  value={selectedJobId || undefined}
                  onChange={(v) => setSelectedJobId(v)}
                  options={jobs.map((j) => ({
                    value: j.id,
                    label: `${j.title} — ${j.company}`,
                  }))}
                />
                <Button
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setModalQuestion('')
                    setModalCategory('FACTUAL')
                    setModalAnswer('')
                    setAnswerModalOpen(true)
                  }}
                >
                  Add Answer to Library
                </Button>
              </Space>
            </Col>
          </Row>
        </Card>

        {pack && (
          <>
            <Card loading={loading}>
              <Row gutter={[24, 24]} align="middle">
                <Col xs={24} md={6} style={{ textAlign: 'center' }}>
                  <Progress
                    type="dashboard"
                    percent={pack.readinessScore}
                    status={pack.readinessScore >= pack.readinessBreakdown.userThreshold ? 'success' : 'exception'}
                  />
                  <div>
                    <Text strong>Readiness Score (0–100)</Text>
                  </div>
                  <Tag color={pack.routesToReviewQueue ? 'gold' : 'green'} style={{ marginTop: 6 }}>
                    {pack.routesToReviewQueue ? 'Routes to Review Queue' : 'Ready for Assisted Apply'}
                  </Tag>
                </Col>
                <Col xs={24} md={18}>
                  <Descriptions title="Application Pack & Snapshot Audit" bordered size="small" column={{ xs: 1, sm: 2 }}>
                    <Descriptions.Item label="Job">{`${pack.jobSnapshot.title} @ ${pack.jobSnapshot.company}`}</Descriptions.Item>
                    <Descriptions.Item label="Submission State">
                      <Tag color={submissionState === 'SUBMITTED_CONFIRMED' ? 'green' : 'blue'}>
                        {submissionState || 'PACK_READY'}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Resume Version">
                      {pack.resumeFilename || 'Default Verified Resume'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Truthfulness Check">
                      <Tag color={pack.truthfulnessPassed ? 'green' : 'red'}>
                        {pack.truthfulnessPassed ? 'PASSED (All claims grounded)' : 'BLOCKED (Unsupported claim)'}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Job Snapshot SHA-256" span={2}>
                      <Text code>{pack.jobSnapshotHash}</Text>
                    </Descriptions.Item>
                  </Descriptions>

                  <Space style={{ marginTop: 16 }} wrap>
                    <Button
                      icon={<LinkOutlined />}
                      href={currentJob?.sourceUrl || 'https://example.com/careers'}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Open Original Page
                    </Button>
                    <Button
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      disabled={submissionState === 'SUBMITTED_CONFIRMED'}
                      onClick={handleConfirmApplied}
                    >
                      {submissionState === 'SUBMITTED_CONFIRMED'
                        ? 'Applied (USER_CONFIRMED)'
                        : 'I Applied (Set USER_CONFIRMED & Auto-Schedule Follow-Up)'}
                    </Button>
                  </Space>
                </Col>
              </Row>

              {pack.readinessBreakdown.routingReasons.length > 0 && (
                <Alert
                  style={{ marginTop: 16 }}
                  type="warning"
                  showIcon
                  message="Why this pack routes to Review Queue before submission:"
                  description={pack.readinessBreakdown.routingReasons.join(' | ')}
                />
              )}
            </Card>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card
                  title="Tone 1: Formal Cover Note"
                  extra={
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copyText('Formal Note', pack.notesThreeTones.formal)}
                    >
                      Copy
                    </Button>
                  }
                >
                  <Paragraph>{pack.notesThreeTones.formal}</Paragraph>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card
                  title="Tone 2: Concise Cover Note"
                  extra={
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copyText('Concise Note', pack.notesThreeTones.concise)}
                    >
                      Copy
                    </Button>
                  }
                >
                  <Paragraph>{pack.notesThreeTones.concise}</Paragraph>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card
                  title="Tone 3: Enthusiastic Cover Note"
                  extra={
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copyText('Enthusiastic Note', pack.notesThreeTones.enthusiastic)}
                    >
                      Copy
                    </Button>
                  }
                >
                  <Paragraph>{pack.notesThreeTones.enthusiastic}</Paragraph>
                </Card>
              </Col>
            </Row>

            <Card
              title={
                <Space>
                  <BookOutlined />
                  <span>Screening Questions & Answer Library Resolution</span>
                </Space>
              }
            >
              <List
                dataSource={pack.answers}
                renderItem={(ans) => (
                  <List.Item
                    actions={[
                      ans.approvedAnswer ? (
                        <Button
                          key="copy"
                          size="small"
                          icon={<CopyOutlined />}
                          onClick={() => copyText('Answer', ans.approvedAnswer!)}
                        >
                          Copy Answer
                        </Button>
                      ) : null,
                      <Button
                        key="save"
                        size="small"
                        type={ans.offerToSaveNewAnswer ? 'primary' : 'default'}
                        onClick={() => {
                          setModalQuestion(ans.questionText)
                          setModalCategory(ans.category)
                          setModalAnswer(ans.approvedAnswer || '')
                          setAnswerModalOpen(true)
                        }}
                      >
                        {ans.offerToSaveNewAnswer ? 'Save Answer to Library' : 'Edit in Library'}
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Space wrap>
                          <Text strong>{ans.questionText}</Text>
                          <Tag color="geekblue">{ans.category}</Tag>
                          <Tag color={ans.requiresUserPause ? 'volcano' : 'green'}>
                            {ans.requiresUserPause ? 'PAUSED FOR USER' : 'AUTO-FILL READY'}
                          </Tag>
                          <Tag>Source: {ans.source}</Tag>
                          <Tag>Confidence: {Math.round(ans.confidence * 100)}%</Tag>
                        </Space>
                      }
                      description={
                        <div>
                          <div>
                            <Text strong>Approved Answer: </Text>
                            <Text>{ans.approvedAnswer || '— (Requires user input)'}</Text>
                          </div>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {ans.reason}
                          </Text>
                        </div>
                      }
                    />
                  </List.Item>
                )}
              />
            </Card>
          </>
        )}
      </Space>

      <Modal
        title="Save Approved Answer to Answer Library"
        open={answerModalOpen}
        onCancel={() => setAnswerModalOpen(false)}
        onOk={handleSaveToAnswerLibrary}
        okText="Save Approved Answer"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text strong>Question Text</Text>
          <Input value={modalQuestion} onChange={(e) => setModalQuestion(e.target.value)} />
          <Divider style={{ margin: '8px 0' }} />
          <Text strong>Category</Text>
          <Select
            style={{ width: '100%' }}
            value={modalCategory}
            onChange={(v) => setModalCategory(v)}
            options={[
              { value: 'FACTUAL', label: 'FACTUAL (Auto-fill allowed with exact match)' },
              { value: 'PREFERENCE', label: 'PREFERENCE (Auto-fill allowed with exact match)' },
              { value: 'NUMERIC', label: 'NUMERIC (Requires review)' },
              { value: 'FREE_TEXT', label: 'FREE_TEXT (Pre-approved template)' },
              { value: 'LEGAL_CONSENT', label: 'LEGAL_CONSENT (Always pauses for user)' },
              { value: 'UNKNOWN', label: 'UNKNOWN (Always pauses for user)' },
            ]}
          />
          <Divider style={{ margin: '8px 0' }} />
          <Text strong>Approved Answer</Text>
          <Input.TextArea
            rows={3}
            value={modalAnswer}
            onChange={(e) => setModalAnswer(e.target.value)}
            placeholder="Enter truthful, verified answer..."
          />
        </Space>
      </Modal>
    </div>
  )
}
