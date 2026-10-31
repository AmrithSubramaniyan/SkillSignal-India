import React, { useEffect, useState } from 'react'
import {
  Card,
  Switch,
  List,
  Typography,
  Button,
  Space,
  Tag,
  Popconfirm,
  Table,
  Alert,
  Spin,
  message,
} from 'antd'
import { DownloadOutlined, DeleteOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'

const { Title, Paragraph, Text } = Typography

interface ConsentDto {
  consentType: string
  consentVersion: string
  granted: boolean
  grantedAt: string | null
  revokedAt: string | null
  updatedAt: string
}

interface AuditLogEntry {
  id: string
  action: string
  entityType: string
  entityId: string
  metadataJson: string
  createdAt: string
}

const CONSENT_LABELS: Record<string, { title: string; description: string }> = {
  STORE_RESUME_ANSWERS: {
    title: 'Store Resume & Application Answers',
    description: 'Allow SkillSignal to store uploaded resume files, parsed text, and your verified skills.',
  },
  ENABLE_PORTAL_APPLY: {
    title: 'Enable Assisted Portal Apply',
    description: 'Permit local assisted browser form-filling on supported portals (default = Assisted Apply only).',
  },
  ENABLE_EMAIL_SENDING: {
    title: 'Enable Email Application Sending',
    description: 'Permit preparing and sending job applications via your connected email account.',
  },
}

export default function PrivacyPage() {
  const { logout } = useAuth()
  const [consents, setConsents] = useState<ConsentDto[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = async () => {
    setLoading(true)
    try {
      const [cRes, aRes] = await Promise.all([
        apiClient.get<ConsentDto[]>('/consents'),
        apiClient.get<AuditLogEntry[]>('/privacy/audit-logs'),
      ])
      setConsents(cRes.data)
      setAuditLogs(aRes.data)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to load privacy settings.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  const onToggleConsent = async (consentType: string, granted: boolean) => {
    try {
      await apiClient.put(`/consents/${consentType}`, {
        granted,
        consentVersion: 'v1.0',
      })
      message.success(`Consent ${granted ? 'granted' : 'revoked'} for ${consentType}`)
      await loadData()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not update consent.')
    }
  }

  const onExportJson = async () => {
    try {
      const res = await apiClient.get('/privacy/export')
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `skillsignal-data-export-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      message.success('Personal data export downloaded.')
      await loadData()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Export failed.')
    }
  }

  const onDeleteAccount = async () => {
    try {
      await apiClient.delete('/privacy/account')
      message.success('Your account, records, and uploaded files have been permanently erased.')
      await logout()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Account deletion failed.')
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading privacy & consent controls..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 960, margin: '24px auto', padding: '0 24px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Title level={3} style={{ marginTop: 0 }}>
            <SafetyCertificateOutlined /> Privacy & Data Controls
          </Title>
          <Paragraph type="secondary">
            Manage versioned, revocable consents, export all your personal data as JSON, inspect your security audit log, or permanently erase your account and files.
          </Paragraph>

          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

          <List
            header={<Text strong>Granular Consents</Text>}
            bordered
            dataSource={consents}
            renderItem={(item) => {
              const info = CONSENT_LABELS[item.consentType] || {
                title: item.consentType,
                description: '',
              }
              return (
                <List.Item
                  actions={[
                    <Switch
                      key="toggle"
                      checked={item.granted}
                      onChange={(checked) => onToggleConsent(item.consentType, checked)}
                      checkedChildren="Granted"
                      unCheckedChildren="Revoked"
                    />,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <span>{info.title}</span>
                        <Tag>{item.consentVersion}</Tag>
                      </Space>
                    }
                    description={
                      <div>
                        <div>{info.description}</div>
                        {item.granted && item.grantedAt && (
                          <Text type="success" style={{ fontSize: 12 }}>
                            Granted at: {new Date(item.grantedAt).toLocaleString()}
                          </Text>
                        )}
                        {!item.granted && item.revokedAt && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            Revoked at: {new Date(item.revokedAt).toLocaleString()}
                          </Text>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )
            }}
          />

          <div style={{ marginTop: 24, display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
            <Button icon={<DownloadOutlined />} type="primary" onClick={onExportJson}>
              Export All My Data (JSON)
            </Button>

            <Popconfirm
              title="Permanently Delete Account & Files?"
              description="This immediately erases your database records and all uploaded resume files. This cannot be undone."
              okText="Yes, Erase Everything"
              okButtonProps={{ danger: true }}
              onConfirm={onDeleteAccount}
            >
              <Button danger icon={<DeleteOutlined />}>
                Delete My Account & Data
              </Button>
            </Popconfirm>
          </div>
        </Card>

        <Card title="Recent Security & Privacy Audit Log">
          <Table
            rowKey="id"
            size="small"
            pagination={{ pageSize: 8 }}
            dataSource={auditLogs}
            columns={[
              {
                title: 'Timestamp',
                dataIndex: 'createdAt',
                render: (v: string) => new Date(v).toLocaleString(),
              },
              {
                title: 'Action',
                dataIndex: 'action',
                render: (v: string) => <Tag color="blue">{v}</Tag>,
              },
              {
                title: 'Entity',
                dataIndex: 'entityType',
              },
              {
                title: 'Metadata',
                dataIndex: 'metadataJson',
              },
            ]}
          />
        </Card>
      </Space>
    </div>
  )
}
