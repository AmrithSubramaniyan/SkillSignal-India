import React, { useEffect, useState } from 'react'
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
  Alert,
  Spin,
  Empty,
  Collapse,
  Popconfirm,
  message,
} from 'antd'
import {
  UploadOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  FileTextOutlined,
  SaveOutlined,
  StarOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Paragraph, Text } = Typography

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

export default function ResumesPage() {
  const [versions, setVersions] = useState<ResumeVersionDto[]>([])
  const [skillCatalog, setSkillCatalog] = useState<SkillSummaryDto[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editingSkills, setEditingSkills] = useState<Record<string, string[]>>({})
  const [editingYears, setEditingYears] = useState<Record<string, number>>({})
  const [savingId, setSavingId] = useState<string | null>(null)

  const loadResumes = async () => {
    setLoading(true)
    try {
      const [vRes, sRes] = await Promise.all([
        apiClient.get<ResumeVersionDto[]>('/resumes/versions'),
        apiClient.get<SkillSummaryDto[]>('/skills'),
      ])
      setVersions(vRes.data)
      setSkillCatalog(sRes.data)
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

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading resumes..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 960, margin: '24px auto', padding: '0 24px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                <FileTextOutlined /> Resume Versions & Verified Skills
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Upload PDF or DOCX resumes (max 5 MB, up to 3 versions). Review and verify extracted skills — only verified skills count toward job match scores.
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

