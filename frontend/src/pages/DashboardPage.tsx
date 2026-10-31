import React, { useEffect, useState } from 'react'
import {
  Card,
  Row,
  Col,
  Statistic,
  Tag,
  Typography,
  Space,
  List,
  Button,
  Spin,
  Alert,
  Empty,
} from 'antd'
import {
  CheckCircleOutlined,
  DatabaseOutlined,
  AimOutlined,
  FileDoneOutlined,
  BellOutlined,
  RightOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { ApplicationDto, statusColor } from './TrackerPage'

const { Title, Paragraph, Text } = Typography

interface HealthResponse {
  status: string
  service?: string
  dbTime: string
}

interface DashboardSummaryDto {
  totalJobs: number
  totalApplications: number
  statusCounts: Record<string, number>
  followUpsDueCount: number
  followUpsDue: ApplicationDto[]
  estimatedAverageMatchScore: number | null
  metricsDisclaimer: string
}

export default function DashboardPage() {
  const { user } = useAuth()
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [dashboard, setDashboard] = useState<DashboardSummaryDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      apiClient.get<HealthResponse>('/health'),
      apiClient.get<DashboardSummaryDto>('/dashboard'),
    ])
      .then(([hRes, dRes]) => {
        setHealth(hRes.data)
        setDashboard(dRes.data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err?.response?.data?.error || err.message || 'Unable to load dashboard')
        setLoading(false)
      })
  }, [])

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading dashboard & system status..." />
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
                Welcome, {user?.fullName || 'Job Seeker'}
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time.
              </Paragraph>
            </div>
            {health && (
              <Space wrap>
                <Tag icon={<CheckCircleOutlined />} color="success">
                  API: {health.status}
                </Tag>
                <Tag icon={<DatabaseOutlined />} color="processing">
                  DB Time: {health.dbTime}
                </Tag>
              </Space>
            )}
          </div>
          {error && <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />}
        </Card>

        {dashboard && (
          <>
            <Alert type="info" showIcon message={dashboard.metricsDisclaimer} />

            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Saved Jobs"
                    value={dashboard.totalJobs}
                    prefix={<AimOutlined />}
                  />
                  <Link to="/jobs">
                    <Button type="link" size="small" style={{ paddingLeft: 0 }}>
                      Analyze Jobs <RightOutlined />
                    </Button>
                  </Link>
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Tracked Applications"
                    value={dashboard.totalApplications}
                    prefix={<FileDoneOutlined />}
                  />
                  <Link to="/tracker">
                    <Button type="link" size="small" style={{ paddingLeft: 0 }}>
                      Open Tracker <RightOutlined />
                    </Button>
                  </Link>
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Follow-Ups Due"
                    value={dashboard.followUpsDueCount}
                    valueStyle={{ color: dashboard.followUpsDueCount > 0 ? '#cf1322' : undefined }}
                    prefix={<BellOutlined />}
                  />
                  <Link to="/tracker">
                    <Button type="link" size="small" style={{ paddingLeft: 0 }}>
                      Review Follow-Ups <RightOutlined />
                    </Button>
                  </Link>
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Est. Avg Match Score"
                    value={
                      dashboard.estimatedAverageMatchScore !== null
                        ? `${dashboard.estimatedAverageMatchScore}%`
                        : 'N/A'
                    }
                  />
                  <Link to="/resumes">
                    <Button type="link" size="small" style={{ paddingLeft: 0 }}>
                      Manage Verified Skills <RightOutlined />
                    </Button>
                  </Link>
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card title="Application Pipeline Breakdown">
                  <Space wrap size={[8, 12]}>
                    {Object.entries(dashboard.statusCounts).map(([status, count]) => (
                      <Card.Grid key={status} style={{ width: '33.33%', textAlign: 'center', padding: 12 }}>
                        <div>
                          <Tag color={statusColor(status)}>{status}</Tag>
                        </div>
                        <Title level={4} style={{ margin: '6px 0 0' }}>
                          {count}
                        </Title>
                      </Card.Grid>
                    ))}
                  </Space>
                </Card>
              </Col>

              <Col xs={24} md={12}>
                <Card
                  title={
                    <Space>
                      <BellOutlined />
                      <span>Follow-Ups Due Today / Overdue ({dashboard.followUpsDueCount})</span>
                    </Space>
                  }
                  extra={<Link to="/tracker">View All</Link>}
                >
                  {dashboard.followUpsDue.length === 0 ? (
                    <Empty description="No overdue or due follow-ups today." />
                  ) : (
                    <List
                      dataSource={dashboard.followUpsDue}
                      renderItem={(item) => (
                        <List.Item
                          actions={[
                            <Tag key="due" color="volcano">
                              Due: {item.followUpDate}
                            </Tag>,
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Text strong>{item.jobTitle}</Text>
                                <Tag color="blue">{item.company}</Tag>
                                <Tag
                                  icon={
                                    item.status === 'NEEDS_VERIFICATION' ? (
                                      <ExclamationCircleOutlined />
                                    ) : undefined
                                  }
                                  color={statusColor(item.status)}
                                >
                                  {item.status}
                                </Tag>
                              </Space>
                            }
                            description={item.notes}
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>
            </Row>
          </>
        )}
      </Space>
    </div>
  )
}

