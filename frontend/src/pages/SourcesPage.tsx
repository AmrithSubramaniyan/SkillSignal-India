import { useEffect, useState } from 'react'
import { Card, Table, Tag, Typography, Switch, Alert, Space, Button, message } from 'antd'
import { ApiOutlined, SafetyOutlined, ReloadOutlined } from '@ant-design/icons'
import { apiClient as api } from '../api/client'
import { useAuth } from '../context/AuthContext'

const { Title, Text, Paragraph } = Typography

interface SourceCapabilityItem {
  id: string
  source: string
  accessMethod: string
  allowedActions: string[]
  rateLimitPerMinute: number
  termsReviewedAt: string | null
  confirmationSignals: string[]
  enabled: boolean
}

export default function SourcesPage() {
  const { user } = useAuth()
  const [sources, setSources] = useState<SourceCapabilityItem[]>([])
  const [loading, setLoading] = useState(false)

  const fetchSources = async () => {
    setLoading(true)
    try {
      const res = await api.get<SourceCapabilityItem[]>('/sources/capabilities')
      setSources(res.data)
    } catch {
      message.error('Failed to load source capabilities')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchSources()
  }, [])

  const handleToggleSource = async (record: SourceCapabilityItem, nextEnabled: boolean) => {
    try {
      await api.put(`/sources/capabilities/${record.source}/admin-switch`, {
        enabled: nextEnabled,
        allowedActions: record.allowedActions,
      })
      message.success(`Updated source ${record.source}`)
      fetchSources()
    } catch (err: any) {
      message.error(err.response?.data?.error || 'Admin permission required to toggle sources')
    }
  }

  return (
    <div style={{ maxWidth: 1150, margin: '24px auto', padding: '0 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            <ApiOutlined /> Connected Sources & Permissions
          </Title>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            Every source defaults to <strong>Assisted Apply</strong> only. Portals without verified API access remain disabled and are never scraped.
          </Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchSources} loading={loading}>
          Refresh
        </Button>
      </div>

      <Alert
        type="info"
        showIcon
        icon={<SafetyOutlined />}
        style={{ marginBottom: 16 }}
        message="Golden Rule: No Scraping & Assisted Apply Default"
        description="SkillSignal India connects only to documented public job-board APIs (such as Greenhouse Public Job Board API) and user-provided entries. Portals marked 'no verified API access' (Naukri, LinkedIn, Indeed, Foundit, Instahyre, Wellfound) cannot be enabled for automated scraping."
      />

      <Card>
        <Table
          rowKey="source"
          loading={loading}
          dataSource={sources}
          pagination={false}
          columns={[
            {
              title: 'Source',
              dataIndex: 'source',
              render: (val: string) => <Text strong>{val}</Text>,
            },
            {
              title: 'Access Method',
              dataIndex: 'accessMethod',
              render: (val: string) =>
                val === 'no verified API access' ? (
                  <Tag color="red">no verified API access</Tag>
                ) : (
                  <Tag color="blue">{val}</Tag>
                ),
            },
            {
              title: 'Allowed Actions (Default Assisted)',
              dataIndex: 'allowedActions',
              render: (actions: string[]) => (
                <Space wrap>
                  {(actions || []).map((a) => (
                    <Tag key={a} color={a === 'ASSISTED_APPLY' ? 'green' : 'geekblue'}>
                      {a}
                    </Tag>
                  ))}
                </Space>
              ),
            },
            {
              title: 'Rate Limit',
              dataIndex: 'rateLimitPerMinute',
              render: (val: number) => (val > 0 ? `${val} req/min` : 'Disabled (0)'),
            },
            {
              title: 'Confirmation Signals',
              dataIndex: 'confirmationSignals',
              render: (signals: string[]) =>
                signals && signals.length > 0 ? (
                  <Space wrap>
                    {signals.map((s) => (
                      <Tag key={s}>{s}</Tag>
                    ))}
                  </Space>
                ) : (
                  <Text type="secondary">None</Text>
                ),
            },
            {
              title: 'Status / Admin Switch',
              key: 'enabled',
              render: (_, record) => (
                <Space>
                  <Tag color={record.enabled ? 'success' : 'default'}>
                    {record.enabled ? 'ENABLED' : 'DISABLED'}
                  </Tag>
                  {user?.role === 'ADMIN' && (
                    <Switch
                      size="small"
                      checked={record.enabled}
                      disabled={record.accessMethod === 'no verified API access'}
                      onChange={(checked) => handleToggleSource(record, checked)}
                    />
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
