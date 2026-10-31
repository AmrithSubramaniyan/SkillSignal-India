import { useEffect, useState } from 'react'
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
  CalendarOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
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

interface ActionItem {
  type: string
  applicationId?: string
  reminderId?: string
  title: string
  dueDate: string
}

interface HighMatchJobItem {
  jobId: string
  title: string
  company: string
  score: number
}

interface DashboardSummaryDto {
  totalJobs: number
  totalApplications: number
  statusCounts: Record<string, number>
  followUpsDueCount: number
  followUpsDue: ApplicationDto[]
  upcomingInterviews: ApplicationDto[]
  awaitingApproval: ApplicationDto[]
  todaysActions: ActionItem[]
  highMatchJobs: HighMatchJobItem[]
  autoApplyStatus: string
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
    <div style={{ maxWidth: 1240, margin: '24px auto', padding: '0 24px' }}>
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
            <Space wrap>
              {dashboard?.autoApplyStatus && (
                <Tag icon={<SafetyCertificateOutlined />} color="blue">
                  Auto-Apply Mode: {dashboard.autoApplyStatus}
                </Tag>
              )}
              {health && (
                <>
                  <Tag icon={<CheckCircleOutlined />} color="success">
                    API: {health.status}
                  </Tag>
                  <Tag icon={<DatabaseOutlined />} color="processing">
                    DB Time: {health.dbTime}
                  </Tag>
                </>
              )}
            </Space>
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
                    title="Today's Actions"
                    value={dashboard.todaysActions?.length ?? dashboard.followUpsDueCount}
                    valueStyle={{
                      color: (dashboard.todaysActions?.length ?? 0) > 0 ? '#cf1322' : undefined,
                    }}
                    prefix={<BellOutlined />}
                  />
                  <Link to="/reminders">
                    <Button type="link" size="small" style={{ paddingLeft: 0 }}>
                      Open Reminders <RightOutlined />
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

            {/* Stage 17 Full Dashboard Panels */}
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card
                  title={
                    <Space>
                      <ThunderboltOutlined />
                      <span>Today&apos;s Actions ({dashboard.todaysActions?.length || 0})</span>
                    </Space>
                  }
                  extra={<Link to="/reminders">All Reminders</Link>}
                >
                  {!dashboard.todaysActions || dashboard.todaysActions.length === 0 ? (
                    <Empty description="No immediate actions due today." />
                  ) : (
                    <List
                      size="small"
                      dataSource={dashboard.todaysActions}
                      renderItem={(act) => (
                        <List.Item
                          actions={[
                            <Tag key="type" color="volcano">
                              {act.type}
                            </Tag>,
                          ]}
                        >
                          <List.Item.Meta
                            title={<Text strong>{act.title}</Text>}
                            description={`Due: ${act.dueDate}`}
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>

              <Col xs={24} md={12}>
                <Card
                  title={
                    <Space>
                      <CalendarOutlined />
                      <span>Upcoming Interviews & Assessments ({dashboard.upcomingInterviews?.length || 0})</span>
                    </Space>
                  }
                  extra={<Link to="/tracker">View Tracker</Link>}
                >
                  {!dashboard.upcomingInterviews || dashboard.upcomingInterviews.length === 0 ? (
                    <Empty description="No upcoming interviews or assessments scheduled." />
                  ) : (
                    <List
                      size="small"
                      dataSource={dashboard.upcomingInterviews}
                      renderItem={(app) => (
                        <List.Item
                          actions={[
                            <Tag key="st" color={statusColor(app.status)}>
                              {app.status}
                            </Tag>,
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Text strong>{app.jobTitle}</Text>
                                <Tag color="blue">{app.company}</Tag>
                              </Space>
                            }
                            description={
                              <Space size="middle">
                                {app.assessmentDate && <Text type="secondary">Assessment: {app.assessmentDate}</Text>}
                                {app.interviewDate && <Text type="secondary">Interview: {app.interviewDate}</Text>}
                              </Space>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card
                  title={
                    <Space>
                      <ExclamationCircleOutlined />
                      <span>Awaiting Approval & Verification ({dashboard.awaitingApproval?.length || 0})</span>
                    </Space>
                  }
                  extra={<Link to="/tracker">Resolve in Tracker</Link>}
                >
                  {!dashboard.awaitingApproval || dashboard.awaitingApproval.length === 0 ? (
                    <Empty description="No applications awaiting approval or verification." />
                  ) : (
                    <List
                      size="small"
                      dataSource={dashboard.awaitingApproval}
                      renderItem={(app) => (
                        <List.Item
                          actions={[
                            <Tag key="st" color="orange">
                              {app.subState === 'NEEDS_VERIFICATION' ? 'NEEDS_VERIFICATION' : app.status}
                            </Tag>,
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Text strong>{app.jobTitle}</Text>
                                <Tag color="blue">{app.company}</Tag>
                              </Space>
                            }
                            description={app.notes || 'Requires user confirmation or verification'}
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>

              <Col xs={24} md={12}>
                <Card
                  title={
                    <Space>
                      <AimOutlined />
                      <span>High-Match Jobs (≥ 75% Estimated Match) ({dashboard.highMatchJobs?.length || 0})</span>
                    </Space>
                  }
                  extra={<Link to="/jobs">Jobs & Match</Link>}
                >
                  {!dashboard.highMatchJobs || dashboard.highMatchJobs.length === 0 ? (
                    <Empty description="Compute job matches on the Jobs & Match page to see high-match roles." />
                  ) : (
                    <List
                      size="small"
                      dataSource={dashboard.highMatchJobs}
                      renderItem={(hm) => (
                        <List.Item
                          actions={[
                            <Tag key="score" color="green">
                              {hm.score}% Est. Match
                            </Tag>,
                          ]}
                        >
                          <List.Item.Meta
                            title={<Text strong>{hm.title}</Text>}
                            description={hm.company}
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24}>
                <Card title="Application Pipeline Breakdown">
                  <Space wrap size={[8, 12]}>
                    {Object.entries(dashboard.statusCounts).map(([status, count]) => (
                      <Card.Grid key={status} style={{ width: '16.66%', minWidth: 140, textAlign: 'center', padding: 12 }}>
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
            </Row>
          </>
        )}
      </Space>
    </div>
  )
}
