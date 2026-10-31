import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DislikeOutlined,
  ExclamationCircleOutlined,
  LikeOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text } = Typography

export default function AutoApplyCentrePage() {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)

  const loadCentre = async () => {
    setLoading(true)
    try {
      const res = await apiClient.get('/api/auto-apply/centre')
      setData(res.data)
    } catch {
      message.error('Failed to load Auto-Apply Centre')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCentre()
  }, [])

  const triggerKillSwitch = async () => {
    try {
      const res = await apiClient.post('/api/safety/kill-switch', {
        reason: 'User clicked Big STOP button in Auto-Apply Centre',
      })
      message.warning(
        `EMERGENCY STOP ACTIVATED: Global state is now STOPPED (${res.data.haltedTasksCount} tasks halted)`
      )
      loadCentre()
    } catch {
      message.error('Failed to trigger kill switch')
    }
  }

  const applySuggestionWithConsent = async (id: string) => {
    try {
      await apiClient.post(`/api/auto-apply/centre/suggestions/${id}/apply`, {
        consentGranted: true,
      })
      message.success('Suggestion applied with your explicit consent')
      loadCentre()
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Failed to apply suggestion')
    }
  }

  const dismissSuggestion = async (id: string) => {
    try {
      await apiClient.post(`/api/auto-apply/centre/suggestions/${id}/dismiss`)
      message.info('Suggestion dismissed')
      loadCentre()
    } catch {
      message.error('Failed to dismiss suggestion')
    }
  }

  const submitSkipFeedback = async (item: any, agreeWithSkip: boolean) => {
    try {
      await apiClient.post('/api/auto-apply/centre/skip-feedback', {
        submissionId: item.submissionId || null,
        jobId: item.jobId || null,
        skipReason: item.reason || 'Skipped',
        agreeWithSkip,
        userComment: agreeWithSkip ? 'Agreed with skip reason' : 'Disagreed with skip reason',
      })
      message.success('Skip-reason feedback recorded')
      loadCentre()
    } catch {
      message.error('Failed to record skip-reason feedback')
    }
  }

  const counts = data?.counts || {}
  const meters = data?.limitMeters || {}
  const timeSaved = data?.estimatedTimeSaved || {}
  const policy = data?.strictPolicySettings || {}
  const learning = data?.learningLoop || {}

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
            <SafetyCertificateOutlined /> Auto-Apply Centre & Learning Loop
          </Title>
          <Text type="secondary">
            Real-time control panel, strict eligibility & safety governor status, blockers, and personal response learning loop.
          </Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadCentre} loading={loading}>
            Refresh
          </Button>
          <Button
            type="primary"
            danger
            size="large"
            icon={<StopOutlined />}
            onClick={triggerKillSwitch}
            style={{ fontWeight: 700 }}
          >
            STOP ALL AUTO-APPLY (KILL SWITCH)
          </Button>
        </Space>
      </div>

      {timeSaved.label && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={timeSaved.label}
          description={`Global State: ${meters.globalState || 'ACTIVE'} | MIN_SKILLS: ${policy.minMatchedSkills ?? 3} | 50% Required-Skill Coverage Guard: ${policy.require50PercentSkillCoverage ? 'ON' : 'OFF'} | Over-Qualified Policy: ${policy.overQualifiedPolicy || 'BLOCK'}`}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6} md={3}>
          <Card size="small">
            <Statistic title="Discovered" value={counts.discovered ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={3}>
          <Card size="small">
            <Statistic title="Matched" value={counts.matched ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={3}>
          <Card size="small">
            <Statistic title="Packs Prepared" value={counts.packsPrepared ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={3}>
          <Card size="small">
            <Statistic title="Awaiting Approval" value={counts.awaitingApproval ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={4}>
          <Card size="small">
            <Statistic
              title="Submitted (Confirmed)"
              value={counts.submittedConfirmed ?? 0}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={4}>
          <Card size="small">
            <Statistic
              title="Needs Verification"
              value={counts.needsVerification ?? 0}
              valueStyle={{ color: '#d48806' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={4}>
          <Card size="small">
            <Statistic
              title="Failed"
              value={counts.failed ?? 0}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card title="Daily & Weekly Rate Limit Meters" size="small">
            <div style={{ marginBottom: 12 }}>
              <Text strong>
                Daily Cap ({meters.dailyUsed ?? 0} / {meters.dailyCap ?? 5})
              </Text>
              <Progress
                percent={
                  meters.dailyCap
                    ? Math.min(100, Math.round(((meters.dailyUsed ?? 0) * 100) / meters.dailyCap))
                    : 0
                }
              />
            </div>
            <div>
              <Text strong>
                Weekly Cap ({meters.weeklyUsed ?? 0} / {meters.weeklyCap ?? 20})
              </Text>
              <Progress
                percent={
                  meters.weeklyCap
                    ? Math.min(100, Math.round(((meters.weeklyUsed ?? 0) * 100) / meters.weeklyCap))
                    : 0
                }
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Blockers Waiting for User Action" size="small">
            {(data?.blockersWaitingForUser || []).length === 0 ? (
              <Text type="secondary">No active blockers waiting for user intervention.</Text>
            ) : (
              <Table
                size="small"
                pagination={false}
                rowKey={(r: any) => r.taskId}
                dataSource={data?.blockersWaitingForUser || []}
                columns={[
                  { title: 'Job', dataIndex: 'jobTitle' },
                  { title: 'Portal', dataIndex: 'portal' },
                  {
                    title: 'Blocker',
                    dataIndex: 'blockerType',
                    render: (v: string) => (
                      <Tag color="warning" icon={<ExclamationCircleOutlined />}>
                        {v}
                      </Tag>
                    ),
                  },
                  { title: 'Message', dataIndex: 'blockerMessage' },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Tabs
        items={[
          {
            key: 'sources',
            label: 'Per-Source Status & Circuit Breakers',
            children: (
              <Card size="small">
                <Table
                  size="small"
                  rowKey="source"
                  pagination={false}
                  dataSource={data?.perSourceStatus || []}
                  columns={[
                    { title: 'Source', dataIndex: 'displayName' },
                    {
                      title: 'Enabled',
                      dataIndex: 'enabled',
                      render: (v: boolean) =>
                        v ? <Tag color="green">Enabled</Tag> : <Tag>Disabled</Tag>,
                    },
                    { title: 'Allowed Actions', dataIndex: 'allowedActions' },
                    { title: 'Login State', dataIndex: 'loginState' },
                    { title: 'Apply Mode', dataIndex: 'applyMode' },
                    {
                      title: 'Circuit Breaker',
                      dataIndex: 'circuitBreakerState',
                      render: (v: string) => (
                        <Tag color={v === 'OPEN' ? 'red' : 'blue'}>{v}</Tag>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'skipped',
            label: `Skipped with Reasons (${(data?.skippedWithReasons || []).length})`,
            children: (
              <Card size="small">
                <Table
                  size="small"
                  rowKey={(r: any, idx) => r.submissionId || r.taskId || String(idx)}
                  dataSource={data?.skippedWithReasons || []}
                  columns={[
                    { title: 'Job', dataIndex: 'jobTitle' },
                    { title: 'Company', dataIndex: 'company' },
                    { title: 'State', dataIndex: 'state', render: (v: string) => <Tag>{v}</Tag> },
                    { title: 'Why Skipped', dataIndex: 'reason' },
                    {
                      title: 'Feedback',
                      render: (_: any, row: any) => (
                        <Space>
                          <Button
                            size="small"
                            icon={<LikeOutlined />}
                            onClick={() => submitSkipFeedback(row, true)}
                          >
                            Good Skip
                          </Button>
                          <Button
                            size="small"
                            icon={<DislikeOutlined />}
                            onClick={() => submitSkipFeedback(row, false)}
                          >
                            Should Have Applied
                          </Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'learning',
            label: 'Learning Loop & Rule Suggestions (Own Data Only)',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Alert
                  type="success"
                  showIcon
                  message={learning.dataSourceNote || 'Computed strictly from your own stored applications'}
                  description={`Sample size (total applied): n = ${learning.sampleSizeTotalApplied ?? 0}. Rule-change suggestions are NEVER applied without your explicit consent.`}
                />
                <Card title="Rule-Change Suggestions (Require Explicit User Consent)" size="small">
                  <Table
                    size="small"
                    rowKey="id"
                    pagination={false}
                    dataSource={learning.ruleChangeSuggestions || []}
                    columns={[
                      { title: 'Suggestion', dataIndex: 'suggestionTitle' },
                      { title: 'Rationale', dataIndex: 'rationale' },
                      {
                        title: 'Status',
                        dataIndex: 'status',
                        render: (v: string) => (
                          <Tag color={v === 'APPLIED_WITH_CONSENT' ? 'green' : 'gold'}>{v}</Tag>
                        ),
                      },
                      {
                        title: 'Action',
                        render: (_: any, row: any) =>
                          row.status === 'PENDING_USER_CONSENT' ? (
                            <Space>
                              <Button
                                size="small"
                                type="primary"
                                icon={<CheckCircleOutlined />}
                                onClick={() => applySuggestionWithConsent(row.id)}
                              >
                                Apply with My Consent
                              </Button>
                              <Button
                                size="small"
                                icon={<CloseCircleOutlined />}
                                onClick={() => dismissSuggestion(row.id)}
                              >
                                Dismiss
                              </Button>
                            </Space>
                          ) : (
                            <Text type="secondary">{row.status}</Text>
                          ),
                      },
                    ]}
                  />
                </Card>
                <Row gutter={[16, 16]}>
                  <Col xs={24} md={12}>
                    <Card title="Responses by Resume Version" size="small">
                      <Table
                        size="small"
                        rowKey="resumeVersion"
                        pagination={false}
                        dataSource={learning.byResumeVersion || []}
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
                    <Card title="Responses by Source & Tier" size="small">
                      <Table
                        size="small"
                        rowKey="source"
                        pagination={false}
                        dataSource={learning.bySource || []}
                        columns={[
                          { title: 'Source', dataIndex: 'source' },
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
                </Row>
              </Space>
            ),
          },
        ]}
      />
    </div>
  )
}
