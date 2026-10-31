import { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  List,
  Tag,
  Typography,
  Space,
  Modal,
  Progress,
  Alert,
  Spin,
  Empty,
  Row,
  Col,
  Divider,
  Checkbox,
  Popconfirm,
  message,
} from 'antd'
import {
  PlusOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  PushpinOutlined,
  BulbOutlined,
  LikeOutlined,
  DislikeOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../api/client'

const { Title, Paragraph, Text } = Typography
const { TextArea } = Input

export interface JobDto {
  id: string
  title: string
  company: string
  location: string
  workMode: string
  sourceUrl: string | null
  rawDescription: string
  requiredSkills: string[]
  niceToHaveSkills: string[]
  minYearsExperience: number | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  salaryPeriod: string | null
  requiredEducation: string | null
  saved: boolean
  stale: boolean
  latestMatchScore: number | null
  createdAt: string
}

export interface MatchResultDto {
  matchId: string
  jobId: string
  resumeVersionId: string
  score: number
  requiredSkillsScore: number
  niceToHaveScore: number
  experienceScore: number
  locationScore: number
  matchedRequired: string[]
  missingRequired: string[]
  matchedNiceToHave: string[]
  missingNiceToHave: string[]
  experienceFit: string
  locationFit: string
  salaryFit: string
  qualificationFit: string
  userFeedback: string | null
  feedbackNote: string | null
  suggestions: string[]
  disclaimer: string
  computedAt: string
}

export interface MissingSkillSummaryItem {
  skill: string
  jobCount: number
  jobTitles: string[]
}

export interface JobComparisonItem {
  jobId: string
  title: string
  company: string
  location: string
  workMode: string
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  salaryPeriod: string | null
  minYearsExperience: number | null
  requiredEducation: string | null
  requiredSkills: string[]
  match: MatchResultDto
}

export default function JobsPage() {
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const [jobs, setJobs] = useState<JobDto[]>([])
  const [missingSkillsSummary, setMissingSkillsSummary] = useState<MissingSkillSummaryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [addModalOpen, setAddModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [selectedJob, setSelectedJob] = useState<JobDto | null>(null)
  const [matchResult, setMatchResult] = useState<MatchResultDto | null>(null)
  const [computingJobId, setComputingJobId] = useState<string | null>(null)
  const [savingTrackerId, setSavingTrackerId] = useState<string | null>(null)
  const [compareIds, setCompareIds] = useState<string[]>([])
  const [compareModalOpen, setCompareModalOpen] = useState(false)
  const [compareItems, setCompareItems] = useState<JobComparisonItem[]>([])
  const [comparing, setComparing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadJobs = async () => {
    setLoading(true)
    try {
      const [jobsRes, missingRes] = await Promise.all([
        apiClient.get<JobDto[]>('/jobs'),
        apiClient.get<MissingSkillSummaryItem[]>('/jobs/missing-skills-summary').catch(() => ({ data: [] })),
      ])
      setJobs(jobsRes.data)
      setMissingSkillsSummary(missingRes.data)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to load jobs.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadJobs()
  }, [])

  const onAddJob = async (values: any) => {
    setSubmitting(true)
    setError(null)
    try {
      const res = await apiClient.post<JobDto>('/jobs', values)
      message.success('Job added and parsed!')
      form.resetFields()
      setAddModalOpen(false)
      await loadJobs()
      await handleViewAndComputeMatch(res.data)
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to add job.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleViewAndComputeMatch = async (job: JobDto) => {
    setSelectedJob(job)
    setComputingJobId(job.id)
    setMatchResult(null)
    try {
      const res = await apiClient.post<MatchResultDto>(`/jobs/${job.id}/match`, {})
      setMatchResult(res.data)
      await loadJobs()
    } catch (err: any) {
      message.warning(
        err?.response?.data?.error ||
          'Upload a resume first on the Resumes page to compute an explainable match score.',
      )
    } finally {
      setComputingJobId(null)
    }
  }

  const handleFeedback = async (feedback: 'THUMBS_UP' | 'THUMBS_DOWN') => {
    if (!selectedJob) return
    try {
      const res = await apiClient.put<MatchResultDto>(`/jobs/${selectedJob.id}/match/feedback`, {
        feedback,
        note: feedback === 'THUMBS_UP' ? 'Accurate match explanation' : 'Needs refinement',
      })
      setMatchResult(res.data)
      message.success(`Recorded ${feedback === 'THUMBS_UP' ? 'thumbs up' : 'thumbs down'} feedback`)
    } catch {
      message.error('Failed to save match feedback')
    }
  }

  const handleToggleCompare = (jobId: string, checked: boolean) => {
    if (checked) {
      if (compareIds.length >= 3) {
        message.warning('Select at most 3 jobs to compare side by side')
        return
      }
      setCompareIds([...compareIds, jobId])
    } else {
      setCompareIds(compareIds.filter((id) => id !== jobId))
    }
  }

  const handleCompareJobs = async () => {
    if (compareIds.length < 2 || compareIds.length > 3) {
      message.warning('Select 2 or 3 jobs to compare')
      return
    }
    setComparing(true)
    try {
      const res = await apiClient.post<JobComparisonItem[]>('/jobs/compare', { jobIds: compareIds })
      setCompareItems(res.data)
      setCompareModalOpen(true)
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not compare jobs (ensure a resume is uploaded)')
    } finally {
      setComparing(false)
    }
  }

  const handleDeleteJob = async (jobId: string) => {
    try {
      await apiClient.delete(`/jobs/${jobId}`)
      message.success('Job removed.')
      if (selectedJob?.id === jobId) {
        setSelectedJob(null)
        setMatchResult(null)
      }
      setCompareIds(compareIds.filter((id) => id !== jobId))
      await loadJobs()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to delete job.')
    }
  }

  const handleSaveToTracker = async (jobId: string) => {
    setSavingTrackerId(jobId)
    try {
      await apiClient.post('/applications', {
        jobId,
        status: 'SAVED',
        notes: 'Saved from Explainable Match analysis',
      })
      message.success('Saved to Application Tracker!')
      navigate('/tracker')
    } catch (err: any) {
      if (err?.response?.status === 409) {
        message.info('This job is already in your Application Tracker.')
        navigate('/tracker')
      } else {
        message.error(err?.response?.data?.error || 'Could not save to tracker.')
      }
    } finally {
      setSavingTrackerId(null)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading jobs..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1180, margin: '24px auto', padding: '0 24px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                Jobs, Full Match Analysis & Side-by-Side Comparison
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Compute transparent 100-point match scores (configurable weights, salary & qualification fit when data exists, aggregated missing skills across saved jobs, and 2–3 job comparison).
              </Paragraph>
            </div>
            <Space>
              <Button
                icon={<SwapOutlined />}
                disabled={compareIds.length < 2 || compareIds.length > 3}
                loading={comparing}
                onClick={handleCompareJobs}
              >
                Compare Selected ({compareIds.length}/3)
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddModalOpen(true)}>
                Add Job (Paste JD / URL)
              </Button>
            </Space>
          </div>
          {error && <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />}
        </Card>

        {missingSkillsSummary.length > 0 && (
          <Card size="small" title="Aggregated Missing Skills Across Your Saved Jobs">
            <Space wrap>
              {missingSkillsSummary.map((item) => (
                <Tag key={item.skill} color="volcano">
                  {item.skill} ({item.jobCount} saved {item.jobCount === 1 ? 'job' : 'jobs'})
                </Tag>
              ))}
            </Space>
          </Card>
        )}

        <Row gutter={24}>
          <Col xs={24} lg={selectedJob ? 10 : 24}>
            {jobs.length === 0 ? (
              <Card>
                <Empty description="No jobs added yet. Click 'Add Job (Paste JD / URL)' to analyze your first role." />
              </Card>
            ) : (
              <List
                dataSource={jobs}
                renderItem={(job) => (
                  <Card
                    key={job.id}
                    style={{
                      marginBottom: 12,
                      borderColor: selectedJob?.id === job.id ? '#1677ff' : undefined,
                    }}
                    title={
                      <Space wrap>
                        <Checkbox
                          checked={compareIds.includes(job.id)}
                          onChange={(e) => handleToggleCompare(job.id, e.target.checked)}
                        />
                        <Text strong>{job.title}</Text>
                        <Tag color="blue">{job.company}</Tag>
                        {job.workMode && <Tag>{job.workMode}</Tag>}
                        {job.location && <Tag>{job.location}</Tag>}
                      </Space>
                    }
                    extra={
                      <Space>
                        {job.latestMatchScore !== null && (
                          <Tag
                            color={
                              job.latestMatchScore >= 75
                                ? 'success'
                                : job.latestMatchScore >= 50
                                  ? 'warning'
                                  : 'error'
                            }
                          >
                            Match: {job.latestMatchScore}%
                          </Tag>
                        )}
                        <Button
                          size="small"
                          type="primary"
                          icon={<ThunderboltOutlined />}
                          loading={computingJobId === job.id}
                          onClick={() => handleViewAndComputeMatch(job)}
                        >
                          Analyze Match
                        </Button>
                        <Popconfirm title="Delete this job?" onConfirm={() => handleDeleteJob(job.id)}>
                          <Button size="small" danger icon={<DeleteOutlined />} />
                        </Popconfirm>
                      </Space>
                    }
                  >
                    <div>
                      <Text type="secondary">Required Skills: </Text>
                      {job.requiredSkills?.length > 0 ? (
                        job.requiredSkills.map((s) => <Tag key={s}>{s}</Tag>)
                      ) : (
                        <Text type="secondary">None explicitly extracted</Text>
                      )}
                    </div>
                  </Card>
                )}
              />
            )}
          </Col>

          {selectedJob && (
            <Col xs={24} lg={14}>
              <Card
                title={`Match Analysis: ${selectedJob.title} @ ${selectedJob.company}`}
                extra={
                  <Space>
                    {matchResult && (
                      <>
                        <Button
                          size="small"
                          type={matchResult.userFeedback === 'THUMBS_UP' ? 'primary' : 'default'}
                          icon={<LikeOutlined />}
                          onClick={() => handleFeedback('THUMBS_UP')}
                        />
                        <Button
                          size="small"
                          danger={matchResult.userFeedback === 'THUMBS_DOWN'}
                          icon={<DislikeOutlined />}
                          onClick={() => handleFeedback('THUMBS_DOWN')}
                        />
                      </>
                    )}
                    <Button
                      type="primary"
                      icon={<PushpinOutlined />}
                      loading={savingTrackerId === selectedJob.id}
                      onClick={() => handleSaveToTracker(selectedJob.id)}
                    >
                      Save to Tracker
                    </Button>
                  </Space>
                }
              >
                {computingJobId === selectedJob.id && (
                  <div style={{ textAlign: 'center', padding: 32 }}>
                    <Spin tip="Computing deterministic Java match score..." />
                  </div>
                )}

                {matchResult && (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Alert
                      type="info"
                      showIcon
                      message="Estimated Relevance Disclaimer"
                      description={matchResult.disclaimer}
                    />

                    <Row gutter={16} align="middle">
                      <Col xs={24} sm={8} style={{ textAlign: 'center' }}>
                        <Progress
                          type="circle"
                          percent={matchResult.score}
                          strokeColor={
                            matchResult.score >= 75
                              ? '#52c41a'
                              : matchResult.score >= 50
                                ? '#faad14'
                                : '#ff4d4f'
                          }
                        />
                        <div style={{ marginTop: 8 }}>
                          <Text strong>Estimated Match Score</Text>
                        </div>
                      </Col>
                      <Col xs={24} sm={16}>
                        <Space direction="vertical" size="small" style={{ width: '100%' }}>
                          <div>
                            <Text strong>Required Skills: </Text>
                            <Tag color="blue">{matchResult.requiredSkillsScore} / 60</Tag>
                          </div>
                          <div>
                            <Text strong>Nice-to-Have Skills: </Text>
                            <Tag color="cyan">{matchResult.niceToHaveScore} / 10</Tag>
                          </div>
                          <div>
                            <Text strong>Experience Fit ({matchResult.experienceScore}/20): </Text>
                            <Text type="secondary">{matchResult.experienceFit}</Text>
                          </div>
                          <div>
                            <Text strong>Location / Mode Fit ({matchResult.locationScore}/10): </Text>
                            <Text type="secondary">{matchResult.locationFit}</Text>
                          </div>
                          <div>
                            <Text strong>Salary Fit: </Text>
                            <Tag color={matchResult.salaryFit === 'not enough data' ? 'default' : 'gold'}>
                              {matchResult.salaryFit}
                            </Tag>
                          </div>
                          <div>
                            <Text strong>Qualification Fit: </Text>
                            <Tag color={matchResult.qualificationFit === 'not enough data' ? 'default' : 'purple'}>
                              {matchResult.qualificationFit}
                            </Tag>
                          </div>
                        </Space>
                      </Col>
                    </Row>

                    <Divider style={{ margin: '8px 0' }} />

                    <div>
                      <Text strong>Matched Required Skills: </Text>
                      <div style={{ marginTop: 6 }}>
                        {matchResult.matchedRequired.length > 0 ? (
                          matchResult.matchedRequired.map((s) => (
                            <Tag key={s} icon={<CheckCircleOutlined />} color="success">
                              {s}
                            </Tag>
                          ))
                        ) : (
                          <Text type="secondary">None matched yet</Text>
                        )}
                      </div>
                    </div>

                    <div>
                      <Text strong>Missing Required Skills: </Text>
                      <div style={{ marginTop: 6 }}>
                        {matchResult.missingRequired.length > 0 ? (
                          matchResult.missingRequired.map((s) => (
                            <Tag key={s} icon={<CloseCircleOutlined />} color="error">
                              {s}
                            </Tag>
                          ))
                        ) : (
                          <Tag color="success">All required skills matched!</Tag>
                        )}
                      </div>
                    </div>

                    {(matchResult.matchedNiceToHave.length > 0 || matchResult.missingNiceToHave.length > 0) && (
                      <div>
                        <Text strong>Nice-to-Have Skills: </Text>
                        <div style={{ marginTop: 6 }}>
                          {matchResult.matchedNiceToHave.map((s) => (
                            <Tag key={s} icon={<CheckCircleOutlined />} color="success">
                              {s}
                            </Tag>
                          ))}
                          {matchResult.missingNiceToHave.map((s) => (
                            <Tag key={s} icon={<CloseCircleOutlined />} color="error">
                              {s}
                            </Tag>
                          ))}
                        </div>
                      </div>
                    )}

                    <Card size="small" title={<><BulbOutlined /> Actionable Preparation Suggestions</>}>
                      <ul style={{ paddingLeft: 20, margin: 0 }}>
                        {matchResult.suggestions.map((sug, idx) => (
                          <li key={idx} style={{ marginBottom: 6 }}>
                            {sug}
                          </li>
                        ))}
                      </ul>
                    </Card>
                  </Space>
                )}

                <Divider />
                <Title level={5}>Raw Job Description</Title>
                <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 220, overflowY: 'auto', fontSize: 12 }}>
                  {selectedJob.rawDescription}
                </pre>
              </Card>
            </Col>
          )}
        </Row>
      </Space>

      <Modal
        title="Compare 2–3 Jobs Side by Side"
        open={compareModalOpen}
        onCancel={() => setCompareModalOpen(false)}
        footer={null}
        width={980}
      >
        <Row gutter={16}>
          {compareItems.map((item) => (
            <Col key={item.jobId} xs={24} md={compareItems.length === 2 ? 12 : 8}>
              <Card
                size="small"
                title={`${item.title} @ ${item.company}`}
                extra={<Tag color="blue">{item.match.score}% Match</Tag>}
              >
                <p>
                  <Text strong>Location / Mode:</Text> {item.location || 'Unspecified'} ({item.workMode})
                </p>
                <p>
                  <Text strong>Salary Fit:</Text> {item.match.salaryFit}
                </p>
                <p>
                  <Text strong>Qualification Fit:</Text> {item.match.qualificationFit}
                </p>
                <p>
                  <Text strong>Experience Fit:</Text> {item.match.experienceFit}
                </p>
                <p>
                  <Text strong>Matched Skills:</Text>{' '}
                  {item.match.matchedRequired.map((s) => (
                    <Tag key={s} color="success">
                      {s}
                    </Tag>
                  ))}
                </p>
                <p>
                  <Text strong>Missing Skills:</Text>{' '}
                  {item.match.missingRequired.map((s) => (
                    <Tag key={s} color="error">
                      {s}
                    </Tag>
                  ))}
                </p>
              </Card>
            </Col>
          ))}
        </Row>
      </Modal>

      <Modal
        title="Add Job from Pasted Description / URL"
        open={addModalOpen}
        onCancel={() => setAddModalOpen(false)}
        footer={null}
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={onAddJob}>
          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item name="title" label="Job Title (Optional — auto-extracted if blank)">
                <Input placeholder="e.g., Senior Backend Engineer" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="company" label="Company Name">
                <Input placeholder="e.g., Razorpay / Apollo Hospitals" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col xs={24} sm={8}>
              <Form.Item name="location" label="City / Location">
                <Input placeholder="e.g., Bengaluru / Mumbai" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="workMode" label="Work Mode">
                <Select
                  allowClear
                  placeholder="Auto-detect"
                  options={[
                    { label: 'Hybrid', value: 'HYBRID' },
                    { label: 'Remote', value: 'REMOTE' },
                    { label: 'On-site', value: 'ONSITE' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="sourceUrl" label="Job Posting URL (Optional)">
                <Input placeholder="https://..." />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col xs={24} sm={8}>
              <Form.Item name="salaryMin" label="Min Salary (LPA, Optional)">
                <InputNumber style={{ width: '100%' }} min={0} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="salaryMax" label="Max Salary (LPA, Optional)">
                <InputNumber style={{ width: '100%' }} min={0} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="requiredEducation" label="Required Qualification (Optional)">
                <Input placeholder="e.g., B.Tech / CA / MBBS" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="rawDescription"
            label="Paste Full Job Description (max 15,000 chars)"
            rules={[{ required: true, message: 'Please paste the job description text' }]}
          >
            <TextArea
              rows={7}
              maxLength={15000}
              showCount
              placeholder="Paste the complete job description here. AI will extract required skills, nice-to-have skills, experience requirements, and location."
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Parse Job & Compute Match Score
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
