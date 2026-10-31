import React, { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Form,
  Input,
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
  suggestions: string[]
  disclaimer: string
  computedAt: string
}

export default function JobsPage() {
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const [jobs, setJobs] = useState<JobDto[]>([])
  const [loading, setLoading] = useState(true)
  const [addModalOpen, setAddModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [selectedJob, setSelectedJob] = useState<JobDto | null>(null)
  const [matchResult, setMatchResult] = useState<MatchResultDto | null>(null)
  const [computingJobId, setComputingJobId] = useState<string | null>(null)
  const [savingTrackerId, setSavingTrackerId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadJobs = async () => {
    setLoading(true)
    try {
      const res = await apiClient.get<JobDto[]>('/jobs')
      setJobs(res.data)
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

  const handleDeleteJob = async (jobId: string) => {
    try {
      await apiClient.delete(`/jobs/${jobId}`)
      message.success('Job removed.')
      if (selectedJob?.id === jobId) {
        setSelectedJob(null)
        setMatchResult(null)
      }
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
    <div style={{ maxWidth: 1120, margin: '24px auto', padding: '0 24px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                Jobs & Explainable Match Score
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Paste any job description (software, healthcare, finance, sales, teaching, design, engineering, legal) to extract requirements and compute a transparent 100-point match score against your verified skills.
              </Paragraph>
            </div>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddModalOpen(true)}>
              Add Job (Paste JD / URL)
            </Button>
          </div>
          {error && <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />}
        </Card>

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
                  <Button
                    type="primary"
                    icon={<PushpinOutlined />}
                    loading={savingTrackerId === selectedJob.id}
                    onClick={() => handleSaveToTracker(selectedJob.id)}
                  >
                    Save to Tracker
                  </Button>
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
          <Form.Item
            name="rawDescription"
            label="Paste Full Job Description (max 15,000 chars)"
            rules={[{ required: true, message: 'Please paste the job description text' }]}
          >
            <TextArea
              rows={8}
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

