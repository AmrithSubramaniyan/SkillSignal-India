import { useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Row, Statistic, Table, Tag, Typography, message } from 'antd'
import { BarChartOutlined, ReloadOutlined } from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text } = Typography

export default function AnalyticsPage() {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)

  const loadAnalytics = async () => {
    setLoading(true)
    try {
      const res = await apiClient.get('/api/analytics')
      setData(res.data)
    } catch {
      message.error('Failed to load Job-Search Analytics')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAnalytics()
  }, [])

  const rr = data?.responseRate || {}
  const fu = data?.followUpCompletion || {}
  const funnel = data?.discoveredVsSavedVsApplied || {}
  const sal = data?.disclosedSalaryRangesOnly || {}

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <Title level={2} style={{ margin: 0 }}>
            <BarChartOutlined /> Job-Search Analytics
          </Title>
          <Text type="secondary">
            Six core job-search metrics with explicit sample sizes, response rate formula, disclosed salary ranges only, and source health table.
          </Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={loadAnalytics} loading={loading}>
          Refresh
        </Button>
      </div>

      {rr.formula && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Response Rate Formula: ${rr.formula}`}
          description={`All metrics display exact sample sizes (n) from your own stored records. ${sal.note || ''}`}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title={`Response Rate (n = ${rr.sampleSize ?? 0})`}
              value={rr.ratePercent ?? 0}
              suffix={`% (${rr.positiveResponses ?? 0}/${rr.totalApplied ?? 0})`}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title={`Follow-Up Completion (n = ${fu.sampleSize ?? 0})`}
              value={fu.completionRatePercent ?? 0}
              suffix={`% (${fu.completedFollowUps ?? 0}/${fu.totalFollowUpReminders ?? 0})`}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title={`Funnel: Discovered / Saved / Applied (n = ${funnel.sampleSize ?? 0})`}
              value={`${funnel.discovered ?? 0} → ${funnel.saved ?? 0} → ${funnel.applied ?? 0}`}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card
            title={`1. Applications Per Week (n = ${data?.applicationsPerWeek?.sampleSize ?? 0})`}
            size="small"
          >
            <Table
              size="small"
              rowKey="week"
              pagination={false}
              dataSource={data?.applicationsPerWeek?.weeks || []}
              columns={[
                { title: 'ISO Week', dataIndex: 'week' },
                { title: 'Applications Count', dataIndex: 'count' },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            title={`2. Applications By Status (n = ${data?.applicationsByStatus?.sampleSize ?? 0})`}
            size="small"
          >
            <Table
              size="small"
              rowKey="status"
              pagination={false}
              dataSource={data?.applicationsByStatus?.breakdown || []}
              columns={[
                { title: 'Status', dataIndex: 'status', render: (v: string) => <Tag>{v}</Tag> },
                { title: 'Count', dataIndex: 'count' },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card
            title={`4. Results By Resume Version (n = ${data?.resultsByResumeVersion?.sampleSize ?? 0})`}
            size="small"
          >
            <Table
              size="small"
              rowKey="resumeVersion"
              pagination={false}
              dataSource={data?.resultsByResumeVersion?.versions || []}
              columns={[
                { title: 'Resume Version', dataIndex: 'resumeVersion' },
                { title: 'Sample (n)', dataIndex: 'sampleSize' },
                { title: 'Positive Responses', dataIndex: 'positiveResponses' },
                {
                  title: 'Response Rate',
                  dataIndex: 'responseRatePercent',
                  render: (v: number) => `${v}%`,
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            title={`Disclosed Salary Ranges Only (n = ${sal.sampleSize ?? 0}, Undisclosed Excluded = ${sal.undisclosedExcludedCount ?? 0})`}
            size="small"
          >
            <Table
              size="small"
              rowKey="jobId"
              pagination={false}
              dataSource={sal.listings || []}
              columns={[
                { title: 'Role', dataIndex: 'title' },
                { title: 'Company', dataIndex: 'company' },
                {
                  title: 'Disclosed Range',
                  render: (_: any, r: any) =>
                    `${r.currency || 'INR'} ${r.salaryMin ?? '?'} - ${r.salaryMax ?? '?'} (${r.period || 'YEARLY'})`,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Source Health Table (Last Success, Errors, Freshness)" size="small">
        <Table
          size="small"
          rowKey="source"
          pagination={false}
          dataSource={data?.sourceHealthTable || []}
          columns={[
            { title: 'Source', dataIndex: 'displayName' },
            {
              title: 'Enabled',
              dataIndex: 'enabled',
              render: (v: boolean) => (v ? <Tag color="green">Enabled</Tag> : <Tag>Disabled</Tag>),
            },
            {
              title: 'Last Success',
              dataIndex: 'lastSuccessAt',
              render: (v: string | null) => v || 'No pulls yet',
            },
            { title: 'Consecutive Errors', dataIndex: 'consecutiveErrors' },
            {
              title: 'Circuit Breaker',
              dataIndex: 'circuitBreakerState',
              render: (v: string) => <Tag color={v === 'OPEN' ? 'red' : 'blue'}>{v}</Tag>,
            },
            {
              title: 'Freshness',
              dataIndex: 'freshness',
              render: (v: string) => (
                <Tag color={v === 'FRESH' ? 'green' : v === 'READY_IDLE' ? 'blue' : 'default'}>
                  {v}
                </Tag>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

