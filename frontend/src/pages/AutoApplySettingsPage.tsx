import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface ExclusionLists {
  companies: string[]
  roles: string[]
  sources: string[]
  keywords: string[]
  countries: string[]
}

interface AutoApplyPreferences {
  userId: string
  minMatchedSkills: number
  require50PercentSkillCoverage: boolean
  overQualifiedPolicy: string
  noExperienceStatedPolicy: string
  readinessThreshold: number
  dailyCap: number
  weeklyCap: number
  duplicateCooldownDays: number
  globalState: string
  enabledSources: string[]
  exclusions: ExclusionLists
}

interface AnswerLibraryItem {
  id: string
  questionText: string
  normalizedQuestion: string
  category: string
  approvedAnswer: string
  confidence: number
}

interface JobItem {
  id: string
  title: string
  company: string
}

interface CheckItem {
  checkNumber: number
  name: string
  passed: boolean
  status: string
  detail: string
}

interface EligibilityResult {
  eligible: boolean
  decision: string
  routesToReviewQueue: boolean
  matchedSkills: string[]
  unmatchedSkills: string[]
  matchedSkillsCount: number
  totalJobRequiredSkillsCount: number
  requiredSkillCoveragePercent: number
  minMatchedSkillsRequired: number
  skillRuleOutcome: string
  experienceRuleOutcome: string
  nineChecks: CheckItem[]
  whyAppliedOrSkipped: string
}

export default function AutoApplySettingsPage() {
  const [prefs, setPrefs] = useState<AutoApplyPreferences | null>(null)
  const [answers, setAnswers] = useState<AnswerLibraryItem[]>([])
  const [jobs, setJobs] = useState<JobItem[]>([])
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)
  const [evalResult, setEvalResult] = useState<EligibilityResult | null>(null)
  const [loading, setLoading] = useState(false)

  const [stopModalOpen, setStopModalOpen] = useState(false)
  const [stopConfirmText, setStopConfirmText] = useState('')

  const [answerModalOpen, setAnswerModalOpen] = useState(false)
  const [prefForm] = Form.useForm()
  const [answerForm] = Form.useForm()

  const loadAll = async () => {
    setLoading(true)
    try {
      const [prefRes, ansRes, jobRes] = await Promise.all([
        apiClient.get<AutoApplyPreferences>('/auto-apply/preferences'),
        apiClient.get<AnswerLibraryItem[]>('/answers'),
        apiClient.get<JobItem[]>('/jobs'),
      ])
      setPrefs(prefRes.data)
      setAnswers(ansRes.data)
      setJobs(jobRes.data)

      const p = prefRes.data
      prefForm.setFieldsValue({
        minMatchedSkills: p.minMatchedSkills,
        require50PercentSkillCoverage: p.require50PercentSkillCoverage,
        overQualifiedPolicy: p.overQualifiedPolicy,
        noExperienceStatedPolicy: p.noExperienceStatedPolicy,
        readinessThreshold: p.readinessThreshold,
        dailyCap: p.dailyCap,
        weeklyCap: p.weeklyCap,
        duplicateCooldownDays: p.duplicateCooldownDays,
        enabledSources: p.enabledSources,
        excludedCompanies: (p.exclusions?.companies || []).join(', '),
        excludedRoles: (p.exclusions?.roles || []).join(', '),
        excludedSources: (p.exclusions?.sources || []).join(', '),
        excludedKeywords: (p.exclusions?.keywords || []).join(', '),
        excludedCountries: (p.exclusions?.countries || []).join(', '),
      })
    } catch {
      message.error('Failed to load auto-apply preferences and answer library')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const splitCsv = (val?: string) =>
    (val || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)

  const handleSavePrefs = async (values: any) => {
    try {
      await apiClient.put('/auto-apply/preferences', {
        minMatchedSkills: values.minMatchedSkills,
        require50PercentSkillCoverage: values.require50PercentSkillCoverage,
        overQualifiedPolicy: values.overQualifiedPolicy,
        noExperienceStatedPolicy: values.noExperienceStatedPolicy,
        readinessThreshold: values.readinessThreshold,
        dailyCap: values.dailyCap,
        weeklyCap: values.weeklyCap,
        duplicateCooldownDays: values.duplicateCooldownDays,
        enabledSources: values.enabledSources,
        exclusions: {
          companies: splitCsv(values.excludedCompanies),
          roles: splitCsv(values.excludedRoles),
          sources: splitCsv(values.excludedSources),
          keywords: splitCsv(values.excludedKeywords),
          countries: splitCsv(values.excludedCountries),
        },
      })
      message.success('Auto-apply rules, caps, and exclusions saved')
      loadAll()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not save preferences')
    }
  }

  const handleGlobalState = async (state: string, confirmationText?: string) => {
    try {
      await apiClient.post('/auto-apply/global-state', { state, confirmationText })
      message.success(`Global state updated to ${state}`)
      setStopModalOpen(false)
      setStopConfirmText('')
      loadAll()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to update global state')
    }
  }

  const handleSaveAnswer = async (values: {
    questionText: string
    category?: string
    approvedAnswer: string
  }) => {
    try {
      await apiClient.post('/answers', {
        questionText: values.questionText,
        category: values.category || null,
        approvedAnswer: values.approvedAnswer,
        confidence: 1.0,
      })
      message.success('Answer saved to library')
      setAnswerModalOpen(false)
      answerForm.resetFields()
      loadAll()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to save answer')
    }
  }

  const handleDeleteAnswer = async (id: string) => {
    try {
      await apiClient.delete(`/answers/${id}`)
      message.success('Answer deleted')
      loadAll()
    } catch {
      message.error('Failed to delete answer')
    }
  }

  const handleEvaluateJob = async () => {
    if (!selectedJobId) return
    try {
      const res = await apiClient.post<EligibilityResult>(`/auto-apply/evaluate/${selectedJobId}`, {})
      setEvalResult(res.data)
    } catch {
      message.error('Could not evaluate job eligibility')
    }
  }

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>
            <SafetyCertificateOutlined /> Auto-Apply Rules, Safety Controls & Answer Library
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Strict Match Rule, 9-check eligibility evaluator, daily/weekly caps, exclusion lists, and verified Answer Library.
          </Paragraph>
        </Col>
        <Col>
          <Space>
            <Tag
              color={
                prefs?.globalState === 'ACTIVE'
                  ? 'green'
                  : prefs?.globalState === 'PAUSED'
                  ? 'gold'
                  : 'red'
              }
            >
              Global State: {prefs?.globalState || 'ACTIVE'}
            </Tag>
            {prefs?.globalState !== 'ACTIVE' && (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={() => handleGlobalState('RESUME')}
              >
                Resume
              </Button>
            )}
            {prefs?.globalState === 'ACTIVE' && (
              <Button icon={<PauseCircleOutlined />} onClick={() => handleGlobalState('PAUSE')}>
                Pause
              </Button>
            )}
            <Button danger icon={<StopOutlined />} onClick={() => setStopModalOpen(true)}>
              Emergency Stop
            </Button>
          </Space>
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'rules',
              label: 'Strict Match Rule, Caps & Exclusions',
              children: (
                <Form form={prefForm} layout="vertical" onFinish={handleSavePrefs} disabled={loading}>
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="minMatchedSkills"
                        label="MIN_SKILLS (Strict Match Rule, Default 3)"
                        rules={[{ required: true }]}
                      >
                        <InputNumber min={1} max={20} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="require50PercentSkillCoverage"
                        label="Require >= 50% Required-Skill Coverage (Recommended Guard)"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="readinessThreshold"
                        label="Readiness Score Threshold (0-100)"
                        rules={[{ required: true }]}
                      >
                        <InputNumber min={0} max={100} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="overQualifiedPolicy" label="Over-Qualified Policy (Above Max Years)">
                        <Select
                          options={[
                            { value: 'BLOCK', label: 'BLOCK (Default)' },
                            { value: 'REVIEW', label: 'Route to Review Queue' },
                            { value: 'ALLOW', label: 'Allow' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="noExperienceStatedPolicy" label="No Experience Stated Policy">
                        <Select
                          options={[
                            { value: 'REVIEW', label: 'Route to Review Queue (Default)' },
                            { value: 'ALLOW', label: 'Allow' },
                            { value: 'BLOCK', label: 'Block' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="duplicateCooldownDays" label="Duplicate Cooldown (Days)">
                        <InputNumber min={0} max={365} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="dailyCap" label="Daily Application Cap (Default 5)">
                        <InputNumber min={0} max={100} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="weeklyCap" label="Weekly Application Cap (Default 20)">
                        <InputNumber min={0} max={500} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="enabledSources" label="Enabled Sources">
                        <Select
                          mode="multiple"
                          options={[
                            { value: 'manual', label: 'manual' },
                            { value: 'company_ats', label: 'company_ats (Public Boards)' },
                            { value: 'email_apply', label: 'email_apply' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Title level={5}>Exclusion Lists (Comma-Separated)</Title>
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="excludedCompanies" label="Excluded Companies">
                        <Input placeholder="e.g. StealthCorp, BadEmployer" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="excludedRoles" label="Excluded Roles / Titles">
                        <Input placeholder="e.g. Unpaid Intern, Cold Calling" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="excludedCountries" label="Excluded Countries">
                        <Input placeholder="e.g. Antarctica" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item name="excludedKeywords" label="Excluded Description Keywords">
                        <Input placeholder="e.g. bond required, unpaid training" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item name="excludedSources" label="Excluded Sources">
                        <Input placeholder="e.g. unverified_portal" />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Button type="primary" htmlType="submit">
                    Save Strict Rules & Safety Controls
                  </Button>
                </Form>
              ),
            },
            {
              key: 'evaluator',
              label: '"Why Applied / Why Skipped" (9-Check Evaluator)',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Space wrap>
                    <Select
                      style={{ width: 420 }}
                      placeholder="Select a stored job to evaluate against the 9 checks"
                      value={selectedJobId}
                      onChange={(v) => setSelectedJobId(v)}
                      options={jobs.map((j) => ({
                        value: j.id,
                        label: `${j.title} @ ${j.company}`,
                      }))}
                    />
                    <Button type="primary" onClick={handleEvaluateJob}>
                      Run 9-Check Eligibility & Strict Rule
                    </Button>
                  </Space>

                  {evalResult && (
                    <Card
                      size="small"
                      title={
                        <Space>
                          <Tag color={evalResult.eligible ? 'green' : 'orange'}>
                            {evalResult.decision}
                          </Tag>
                          <Text strong>
                            Matched {evalResult.matchedSkillsCount}/{evalResult.totalJobRequiredSkillsCount} Required Skills ({evalResult.requiredSkillCoveragePercent}%)
                          </Text>
                        </Space>
                      }
                    >
                      <Alert
                        type={evalResult.eligible ? 'success' : 'warning'}
                        showIcon
                        message="Why Applied / Why Skipped"
                        description={evalResult.whyAppliedOrSkipped}
                        style={{ marginBottom: 16 }}
                      />
                      <Row gutter={16} style={{ marginBottom: 12 }}>
                        <Col span={12}>
                          <Text strong>Matched Verified Skills: </Text>
                          {evalResult.matchedSkills.length === 0 ? (
                            <Text type="secondary">None</Text>
                          ) : (
                            evalResult.matchedSkills.map((s) => (
                              <Tag key={s} color="green">
                                {s}
                              </Tag>
                            ))
                          )}
                        </Col>
                        <Col span={12}>
                          <Text strong>Unmatched Skills: </Text>
                          {evalResult.unmatchedSkills.length === 0 ? (
                            <Text type="secondary">None</Text>
                          ) : (
                            evalResult.unmatchedSkills.map((s) => (
                              <Tag key={s} color="red">
                                {s}
                              </Tag>
                            ))
                          )}
                        </Col>
                      </Row>
                      <List
                        size="small"
                        header={<Text strong>The 9 Eligibility Checks</Text>}
                        bordered
                        dataSource={evalResult.nineChecks}
                        renderItem={(c) => (
                          <List.Item>
                            <Space>
                              {c.passed ? (
                                <CheckCircleOutlined style={{ color: '#52c41a' }} />
                              ) : (
                                <CloseCircleOutlined style={{ color: '#faad14' }} />
                              )}
                              <Text strong>
                                #{c.checkNumber} {c.name}:
                              </Text>
                              <Tag color={c.passed ? 'green' : 'gold'}>{c.status}</Tag>
                              <Text type="secondary">{c.detail}</Text>
                            </Space>
                          </List.Item>
                        )}
                      />
                    </Card>
                  )}
                </Space>
              ),
            },
            {
              key: 'answers',
              label: `Answer Library (${answers.length})`,
              children: (
                <div>
                  <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
                    <Text type="secondary">
                      Auto-fill is permitted ONLY for FACTUAL and PREFERENCE questions. Legal, visa, declaration, background-check, salary-history, demographic, and unknown questions ALWAYS pause for user review.
                    </Text>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => setAnswerModalOpen(true)}
                    >
                      Add Approved Answer
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    dataSource={answers}
                    columns={[
                      { title: 'Question', dataIndex: 'questionText', key: 'questionText' },
                      {
                        title: 'Category',
                        dataIndex: 'category',
                        key: 'category',
                        render: (cat: string) => (
                          <Tag
                            color={
                              cat === 'FACTUAL' || cat === 'PREFERENCE'
                                ? 'green'
                                : cat === 'LEGAL_CONSENT' || cat === 'UNKNOWN'
                                ? 'red'
                                : 'blue'
                            }
                          >
                            {cat}
                          </Tag>
                        ),
                      },
                      {
                        title: 'Approved Answer',
                        dataIndex: 'approvedAnswer',
                        key: 'approvedAnswer',
                      },
                      {
                        title: 'Action',
                        key: 'action',
                        render: (_: unknown, r: AnswerLibraryItem) => (
                          <Button
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleDeleteAnswer(r.id)}
                          />
                        ),
                      },
                    ]}
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* Emergency Stop Confirmation Modal */}
      <Modal
        title="Confirm Emergency Stop"
        open={stopModalOpen}
        onCancel={() => setStopModalOpen(false)}
        onOk={() => handleGlobalState('STOPPED', stopConfirmText)}
        okText="Confirm STOP"
        okButtonProps={{ danger: true }}
      >
        <Paragraph>
          Type <Text code>STOP</Text> below to confirm stopping all auto-apply activity:
        </Paragraph>
        <Input
          value={stopConfirmText}
          onChange={(e) => setStopConfirmText(e.target.value)}
          placeholder="Type STOP"
        />
      </Modal>

      {/* Add Answer Modal */}
      <Modal
        title="Save Approved Answer to Library"
        open={answerModalOpen}
        onCancel={() => setAnswerModalOpen(false)}
        onOk={() => answerForm.submit()}
        okText="Save Answer"
      >
        <Form form={answerForm} layout="vertical" onFinish={handleSaveAnswer}>
          <Form.Item name="questionText" label="Question Text" rules={[{ required: true }]}>
            <Input placeholder="e.g. What is your current city?" />
          </Form.Item>
          <Form.Item name="category" label="Category (Leave blank to auto-classify)">
            <Select
              allowClear
              options={[
                { value: 'FACTUAL', label: 'FACTUAL (Auto-fill allowed)' },
                { value: 'PREFERENCE', label: 'PREFERENCE (Auto-fill allowed)' },
                { value: 'NUMERIC', label: 'NUMERIC' },
                { value: 'FREE_TEXT', label: 'FREE_TEXT (Pre-approved template)' },
                { value: 'LEGAL_CONSENT', label: 'LEGAL_CONSENT (Always pauses)' },
              ]}
            />
          </Form.Item>
          <Form.Item name="approvedAnswer" label="Approved Answer" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="Your verified answer..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

