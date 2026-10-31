import React, { useEffect, useState } from 'react'
import { Card, Typography, Tag, Spin, Alert, Space } from 'antd'
import { CheckCircleOutlined, DatabaseOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Title, Paragraph, Text } = Typography

interface HealthResponse {
  status: string
  service?: string
  dbTime: string
}

export default function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    axios
      .get<HealthResponse>('/api/health')
      .then((res) => {
        setHealth(res.data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err?.response?.data?.error || err.message || 'Unable to reach backend /api/health')
        setLoading(false)
      })
  }, [])

  return (
    <div style={{ maxWidth: 720, margin: '64px auto', padding: '0 24px' }}>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Title level={2} style={{ margin: 0 }}>
            SkillSignal India
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 8 }}>
            Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time.
          </Paragraph>

          <Card size="small" title="System Health (/api/health)">
            {loading && <Spin tip="Checking backend and database..." />}
            {error && <Alert type="error" showIcon message="Backend Unreachable" description={error} />}
            {health && (
              <Space direction="vertical">
                <div>
                  <Text strong>Backend Status: </Text>
                  <Tag icon={<CheckCircleOutlined />} color="success">
                    {health.status}
                  </Tag>
                </div>
                <div>
                  <Text strong>Database Timestamp: </Text>
                  <Tag icon={<DatabaseOutlined />} color="processing">
                    {health.dbTime}
                  </Tag>
                </div>
              </Space>
            )}
          </Card>
        </Space>
      </Card>
    </div>
  )
}

