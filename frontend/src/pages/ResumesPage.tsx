import { useEffect, useState } from 'react'
import {
  Card,
  Upload,
  Button,
  List,
  Tag,
  Typography,
  Space,
  Select,
  InputNumber,
  Input,
  Alert,
  Spin,
  Empty,
  Collapse,
  Popconfirm,
  Row,
  Col,
  Divider,
  message,
} from 'antd'
import {
  UploadOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  FileTextOutlined,
  SaveOutlined,
  StarOutlined,
  HighlightOutlined,
  SafetyCertificateOutlined,
  DownloadOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Paragraph, Text } = Typography
const { TextArea } = Input

export interface ResumeVersionDto {
  id: string
  resumeId: string
  versionNumber: number
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  extractedText: string
  parsedData: {
    skills: string[]
    yearsOfExperience: number | null
    education: string[]
    projects: string[]
  }
  verifiedSkills: string[]
  yearsOfExperience: number | null
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

interface SkillSummaryDto {
  canonicalName: string
  category: string
}

interface JobSummaryOption {
  id: string
  title: string
  company: string
}

interface TailoredResumeDto {
  id: string
  resumeVersionId: string
  jobId: string
  jobTitle: string
  jobCompany: string
  keywordGaps: string[]
  tips: string[]
  originalText: string
  tailoredText: string
  diff: Array<{ section: string; changeType: string; before: string; after: string }>
  truthfulnessPassed: boolean
  unsupportedClaims: string[]
  claimTraces: Array<{ claim: string; category: string; status: string; sourceEvidence: string }>
  userApproved: boolean
  approvedAt: string | null
  createdAt: string
}

export default function ResumesPage() {
  const [versions, setVersions] = useState<ResumeVersionDto[]>([])
  const [skillCatalog, setSkillCatalog] = useState<SkillSummaryDto[]>([])
  const [jobs, setJobs] = useState<JobSummaryOption[]>([])
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)
  const [tailored, setTailored] = useState<TailoredResumeDto | null>(null)
  const [draftEditText, setDraftEditText] = useState<string>('')
  const [tailoringLoading, setTailoringLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editingSkills, setEditingSkills] = useState<Record<string, string[]>>({})
  const [editingYears, setEditingYears] = useState<Record<string, number>>({})
  const [savingId, setSavingId] = useState<string | null>(null)

  const loadResumes = async () => {
    setLoading(true)
    try {
      const [vRes, sRes, jRes] = await Promise.all([
        apiClient.get<ResumeVersionDto[]>('/resumes/versions'),
        apiClient.get<SkillSummaryDto[]>('/skills'),
        apiClient.get<JobSummaryOption[]>('/jobs').catch(() => ({ data: [] })),
      ])
      setVersions(vRes.data)
      setSkillCatalog(sRes.data)
      setJobs(jRes.data)
      if (jRes.data.length > 0 && !selectedJobId) {
        setSelectedJobId(jRes.data[0].id)
      }
      const skillsMap: Record<string, string[]> = {}
      const yearsMap: Record<string, number> = {}
      vRes.data.forEach((v) => {
        skillsMap[v.id] = v.verifiedSkills || []
        yearsMap[v.id] = v.yearsOfExperience ?? 0
      })
      setEditingSkills(skillsMap)
      setEditingYears(yearsMap)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to load resumes.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadResumes()
  }, [])

  const handleUpload = async (file: File) => {
    setError(null)
    setUploading(true)
    const formData = new FormData()
    formData.append('file', file)
    try {
      await apiClient.post('/resumes/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      message.success('Resume uploaded and parsed!')
      await loadResumes()
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Resume upload failed.')
    } finally {
      setUploading(false)
    }
    return false
  }

  const handleSaveSkills = async (versionId: string) => {
    setSavingId(versionId)
    try {
      await apiClient.put(`/resumes/versions/${versionId}/skills`, {
        verifiedSkills: editingSkills[versionId] || [],
        yearsOfExperience: editingYears[versionId] ?? 0,
      })
      message.success('Verified skills updated! These skills will drive job matching.')
      await loadResumes()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to update verified skills.')
    } finally {
      setSavingId(null)
    }
  }

  const handleSetDefault = async (versionId: string) => {
    try {
      await apiClient.put(`/resumes/versions/${versionId}/default`)
      message.success('Default resume version updated.')
      await loadResumes()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not set default version.')
    }
  }

  const handleDelete = async (versionId: string) => {
    try {
      await apiClient.delete(`/resumes/versions/${versionId}`)
      message.success('Resume version deleted.')
      await loadResumes()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not delete resume version.')
    }
  }

  const handleCreateTailoredDraft = async () => {
    if (!selectedJobId) {
      message.warning('Please select a target job first')
      return
    }
    setTailoringLoading(true)
    try {
      const res = await apiClient.post<TailoredResumeDto>('/resumes/tailor', {
        jobId: selectedJobId,
      })
      setTailored(res.data)
      setDraftEditText(res.data.tailoredText)
      message.success('Generated tailored draft and verified truthfulness!')
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not generate tailored resume')
    } finally {
      setTailoringLoading(false)
    }
  }

  const handleVerifyEditedDraft = async () => {
    if (!tailored) return
    setTailoringLoading(true)
    try {
      const res = await apiClient.put<TailoredResumeDto>(`/resumes/tailor/${tailored.id}`, {
        tailoredText: draftEditText,
      })
      setTailored(res.data)
      if (res.data.truthfulnessPassed) {
        message.success('Truthfulness check passed! Every claim traces back to your resume or profile.')
      } else {
        message.error('Truthfulness check BLOCKED unsupported claims.')
      }
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Failed to update tailored draft')
    } finally {
      setTailoringLoading(false)
    }
  }

  const handleApproveDraft = async () => {
    if (!tailored) return
    try {
      const res = await apiClient.post<TailoredResumeDto>(`/resumes/tailor/${tailored.id}/approve`)
      setTailored(res.data)
      message.success('Tailored resume approved! You can now download PDF or DOCX.')
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Truthfulness check blocked approval')
    }
  }

  const handleDownload = async (format: 'pdf' | 'docx') => {
    if (!tailored) return
    try {
      const res = await apiClient.get(`/resumes/tailor/${tailored.id}/download?format=${format}`, {
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `tailored-resume-${tailored.id}.${format}`)
      document.body.appendChild(link)
      link.click()
      link.remove()
    } catch (err: any) {
      message.error('Approve the tailored draft before downloading')
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading resumes..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1080, margin: '24px auto', padding: '0 24px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                <FileTextOutlined /> Resume Versions, Tailoring Studio & Truthfulness Check
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Upload PDF or DOCX resumes (max 5 MB, up to 3 versions), verify skills, and generate job-tailored drafts with automatic claim traceability.
              </Paragraph>
            </div>
            <Upload
              beforeUpload={handleUpload}
              showUploadList={false}
              accept=".pdf,.docx"
              disabled={versions.length >= 3 || uploading}
            >
              <Button
                type="primary"
                icon={<UploadOutlined />}
                loading={uploading}
                disabled={versions.length >= 3}
              >
                Upload PDF / DOCX ({versions.length}/3 Used)
              </Button>
            </Upload>
          </div>

          {error && <Alert type="error" showIcon message={error} closable onClose={() => setError(null)} style={{ marginTop: 16 }} />}
        </Card>

        {versions.length > 0 && (
          <Card
            title={
              <Space>
                <HighlightOutlined />
                <span>Resume Tailoring Studio & Truthfulness Gate (B4–B6)</span>
              </Space>
            }
          >
            <Space wrap style={{ marginBottom: 16 }}>
              <Text strong>Target Job:</Text>
              <Select
                style={{ minWidth: 320 }}
                placeholder="Select a job to tailor your default resume for"
                value={selectedJobId || undefined}
                onChange={(val) => setSelectedJobId(val)}
                options={jobs.map((j) => ({
                  value: j.id,
                  label: `${j.title} @ ${j.company}`,
                }))}
              />
              <Button
                type="primary"
                icon={<HighlightOutlined />}
                loading={tailoringLoading}
                onClick={handleCreateTailoredDraft}
              >
                Generate Tailored Draft & Keyword Gaps
              </Button>
            </Space>

            {tailored && (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Alert
                  type={tailored.truthfulnessPassed ? 'success' : 'error'}
                  showIcon
                  icon={tailored.truthfulnessPassed ? <SafetyCertificateOutlined /> : <StopOutlined />}
                  message={
                    tailored.truthfulnessPassed
                      ? 'Truthfulness Check PASSED — Every skill, degree, and experience claim traces back to your verified resume or profile.'
                      : 'Truthfulness Check BLOCKED — Unsupported claims detected! Approval and export are disabled until unsupported claims are removed.'
                  }
                  description={
                    !tailored.truthfulnessPassed && (
                      <ul style={{ margin: 0, paddingLeft: 18 }}>
                        {tailored.unsupportedClaims.map((uc, idx) => (
                          <li key={idx}>{uc}</li>
                        ))}
                      </ul>
                    )
                  }
                />

                <Row gutter={16}>
                  <Col xs={24} md={12}>
                    <Card size="small" title="Keyword Gap List (Missing from Resume)">
                      {tailored.keywordGaps.length === 0 ? (
                        <Tag color="success">No keyword gaps — all job skills covered!</Tag>
                      ) : (
                        <Space wrap>
                          {tailored.keywordGaps.map((kw) => (
                            <Tag key={kw} color="volcano">
                              {kw}
                            </Tag>
                          ))}
                        </Space>
                      )}
                    </Card>
                  </Col>
                  <Col xs={24} md={12}>
                    <Card size="small" title="3 Actionable Tailoring Tips">
                      <ol style={{ margin: 0, paddingLeft: 18 }}>
                        {tailored.tips.map((tip, idx) => (
                          <li key={idx}>{tip}</li>
                        ))}
                      </ol>
                    </Card>
                  </Col>
                </Row>

                <Row gutter={16}>
                  <Col xs={24} md={12}>
                    <Text strong>Original Resume Text (Before):</Text>
                    <TextArea
                      rows={8}
                      readOnly
                      value={tailored.originalText}
                      style={{ marginTop: 6, background: '#fafafa' }}
                    />
                  </Col>
                  <Col xs={24} md={12}>
                    <Text strong>Tailored Draft (After — Reworded & Reordered Only):</Text>
                    <TextArea
                      rows={8}
                      value={draftEditText}
                      onChange={(e) => setDraftEditText(e.target.value)}
                      style={{ marginTop: 6 }}
                    />
                  </Col>
                </Row>

                <div>
                  <Text strong>Claim Traceability Log: </Text>
                  <Space wrap style={{ marginTop: 6 }}>
                    {tailored.claimTraces.map((ct, idx) => (
                      <Tag key={idx} color={ct.status === 'SUPPORTED' ? 'green' : 'red'}>
                        {ct.claim} [{ct.category}: {ct.status} — {ct.sourceEvidence}]
                      </Tag>
                    ))}
                  </Space>
                </div>

                <Divider style={{ margin: '8px 0' }} />

                <Space wrap>
                  <Button onClick={handleVerifyEditedDraft} loading={tailoringLoading}>
                    Re-Run Truthfulness Check on Edits
                  </Button>
                  <Button
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    disabled={!tailored.truthfulnessPassed || tailored.userApproved}
                    onClick={handleApproveDraft}
                  >
                    {tailored.userApproved ? 'Approved by User' : 'Approve Tailored Draft'}
                  </Button>
                  <Button
                    icon={<DownloadOutlined />}
                    disabled={!tailored.userApproved || !tailored.truthfulnessPassed}
                    onClick={() => handleDownload('pdf')}
                  >
                    Download PDF
                  </Button>
                  <Button
                    icon={<DownloadOutlined />}
                    disabled={!tailored.userApproved || !tailored.truthfulnessPassed}
                    onClick={() => handleDownload('docx')}
                  >
                    Download DOCX
                  </Button>
                </Space>
              </Space>
            )}
          </Card>
        )}

        {versions.length === 0 ? (
          <Card>
            <Empty description="No resume uploaded yet. Upload a PDF or DOCX resume to extract skills and start matching jobs." />
          </Card>
        ) : (
          <List
            grid={{ gutter: 16, column: 1 }}
            dataSource={versions}
            renderItem={(v) => (
              <List.Item>
                <Card
                  title={
                    <Space>
                      <Text strong>
                        v{v.versionNumber} — {v.originalFilename}
                      </Text>
                      {v.isDefault && (
                        <Tag icon={<CheckCircleOutlined />} color="success">
                          Default for Matching
                        </Tag>
                      )}
                      <Tag>{(v.fileSizeBytes / 1024).toFixed(1)} KB</Tag>
                    </Space>
                  }
                  extra={
                    <Space>
                      {!v.isDefault && (
                        <Button size="small" icon={<StarOutlined />} onClick={() => handleSetDefault(v.id)}>
                          Set Default
                        </Button>
                      )}
                      <Popconfirm
                        title="Delete this resume version?"
                        onConfirm={() => handleDelete(v.id)}
                      >
                        <Button size="small" danger icon={<DeleteOutlined />}>
                          Delete
                        </Button>
                      </Popconfirm>
                    </Space>
                  }
                >
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <div>
                      <Text strong>Verified Skills (Edit to add/remove — drives Job Match Score):</Text>
                      <Select
                        mode="tags"
                        style={{ width: '100%', marginTop: 8 }}
                        value={editingSkills[v.id] || []}
                        onChange={(vals) => setEditingSkills({ ...editingSkills, [v.id]: vals })}
                        options={skillCatalog.map((s) => ({
                          label: `${s.canonicalName} (${s.category})`,
                          value: s.canonicalName,
                        }))}
                      />
                    </div>

                    <Space wrap align="center">
                      <div>
                        <Text strong>Verified Years of Experience: </Text>
                        <InputNumber
                          min={0}
                          max={50}
                          step={0.5}
                          value={editingYears[v.id] ?? 0}
                          onChange={(val) =>
                            setEditingYears({ ...editingYears, [v.id]: typeof val === 'number' ? val : 0 })
                          }
                        />
                      </div>
                      <Button
                        type="primary"
                        icon={<SaveOutlined />}
                        loading={savingId === v.id}
                        onClick={() => handleSaveSkills(v.id)}
                      >
                        Save Verified Skills & Experience
                      </Button>
                    </Space>

                    {v.parsedData?.education?.length > 0 && (
                      <div>
                        <Text strong>Extracted Education: </Text>
                        {v.parsedData.education.map((edu, i) => (
                          <Tag key={i} color="geekblue">
                            {edu}
                          </Tag>
                        ))}
                      </div>
                    )}

                    <Collapse
                      size="small"
                      items={[
                        {
                          key: 'text',
                          label: 'View Extracted Resume Text',
                          children: (
                            <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 220, overflowY: 'auto', margin: 0, fontSize: 12 }}>
                              {v.extractedText}
                            </pre>
                          ),
                        },
                      ]}
                    />
                  </Space>
                </Card>
              </List.Item>
            )}
          />
        )}
      </Space>
    </div>
  )
}
