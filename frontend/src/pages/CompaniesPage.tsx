import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  BankOutlined,
  BarChartOutlined,
  DeleteOutlined,
  PlusOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text, Paragraph } = Typography

interface CompanyItem {
  id: string
  name: string
  tier: string
  priority: boolean
  website: string | null
  notes: string | null
  jobsLast30Days: number
  jobsLast60Days: number
  jobsLast90Days: number
  totalStoredJobs: number
  roles: string[]
  locations: string[]
  disclosedSalaryRange: string
  evidenceDisclaimer: string
  updatedAt: string
}

interface HiringIntelligenceSummary {
  totalTrackedCompanies: number
  priorityCompaniesCount: number
  totalStoredJobsAcrossCompanies: number
  disclaimer: string
  companies: CompanyItem[]
}

const TIER_OPTIONS = [
  { value: 'T1', label: 'T1 — Top Target / Dream' },
  { value: 'T2', label: 'T2 — Strong Fit' },
  { value: 'T3', label: 'T3 — Backup / Opportunistic' },
]

export default function CompaniesPage() {
  const [summary, setSummary] = useState<HiringIntelligenceSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [tierFilter, setTierFilter] = useState<string>('')
  const [priorityOnly, setPriorityOnly] = useState<boolean>(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()

  const loadIntelligence = async () => {
    setLoading(true)
    try {
      const params: Record<string, string | boolean> = {}
      if (tierFilter) params.tier = tierFilter
      if (priorityOnly) params.priorityOnly = true
      const res = await apiClient.get<HiringIntelligenceSummary>('/companies/hiring-intelligence', {
        params,
      })
      setSummary(res.data)
    } catch {
      message.error('Failed to load companies and hiring intelligence')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadIntelligence()
  }, [tierFilter, priorityOnly])

  const handleCreateOrUpdate = async (values: {
    name: string
    tier: string
    priority: boolean
    website?: string
    notes?: string
  }) => {
    try {
      await apiClient.post('/companies', values)
      message.success('Company saved')
      setModalOpen(false)
      form.resetFields()
      loadIntelligence()
    } catch (err: any) {
      message.error(err?.response?.data?.error || 'Could not save company')
    }
  }

  const handleTierChange = async (company: CompanyItem, newTier: string) => {
    try {
      await apiClient.put(`/companies/${company.id}`, { tier: newTier })
      message.success(`${company.name} updated to ${newTier}`)
      loadIntelligence()
    } catch {
      message.error('Could not update company tier')
    }
  }

  const handleTogglePriority = async (company: CompanyItem) => {
    try {
      await apiClient.put(`/companies/${company.id}`, { priority: !company.priority })
      message.success(
        !company.priority
          ? `${company.name} added to Priority List`
          : `${company.name} removed from Priority List`
      )
      loadIntelligence()
    } catch {
      message.error('Could not update priority flag')
    }
  }

  const handleDelete = async (company: CompanyItem) => {
    try {
      await apiClient.delete(`/companies/${company.id}`)
      message.success('Company removed')
      loadIntelligence()
    } catch {
      message.error('Could not remove company')
    }
  }

  const companies = summary?.companies || []

  const companyColumns = [
    {
      title: 'Priority',
      key: 'priority',
      width: 90,
      render: (_: unknown, c: CompanyItem) => (
        <Button
          type="text"
          onClick={() => handleTogglePriority(c)}
          icon={
            c.priority ? (
              <StarFilled style={{ color: '#faad14', fontSize: 18 }} />
            ) : (
              <StarOutlined style={{ fontSize: 18 }} />
            )
          }
        />
      ),
    },
    {
      title: 'Company',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, c: CompanyItem) => (
        <div>
          <Text strong>{name}</Text>
          {c.website && (
            <div>
              <a href={c.website} target="_blank" rel="noreferrer" style={{ fontSize: 12 }}>
                {c.website}
              </a>
            </div>
          )}
          {c.notes && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {c.notes}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Tier (T1/T2/T3)',
      dataIndex: 'tier',
      key: 'tier',
      width: 170,
      render: (t: string, c: CompanyItem) => (
        <Select
          size="small"
          value={t}
          style={{ width: 140 }}
          onChange={(val) => handleTierChange(c, val)}
          options={[
            { value: 'T1', label: 'T1 (Top)' },
            { value: 'T2', label: 'T2 (Core)' },
            { value: 'T3', label: 'T3 (Backup)' },
          ]}
        />
      ),
    },
    {
      title: 'Stored Jobs (30 / 60 / 90d)',
      key: 'counts',
      render: (_: unknown, c: CompanyItem) => (
        <Space size={4}>
          <Tag color="blue">30d: {c.jobsLast30Days}</Tag>
          <Tag color="cyan">60d: {c.jobsLast60Days}</Tag>
          <Tag color="geekblue">90d: {c.jobsLast90Days}</Tag>
        </Space>
      ),
    },
    {
      title: 'Disclosed Salary (Stored)',
      dataIndex: 'disclosedSalaryRange',
      key: 'disclosedSalaryRange',
      render: (sal: string) =>
        sal === 'not enough data' ? (
          <Tag color="default">not enough data</Tag>
        ) : (
          <Tag color="green">{sal}</Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 90,
      render: (_: unknown, c: CompanyItem) => (
        <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(c)} />
      ),
    },
  ]

  const intelligenceColumns = [
    {
      title: 'Company & Tier',
      key: 'company',
      render: (_: unknown, c: CompanyItem) => (
        <Space>
          {c.priority && <StarFilled style={{ color: '#faad14' }} />}
          <Text strong>{c.name}</Text>
          <Tag color={c.tier === 'T1' ? 'gold' : c.tier === 'T2' ? 'blue' : 'default'}>
            {c.tier}
          </Tag>
        </Space>
      ),
    },
    {
      title: '30d / 60d / 90d Listings',
      key: 'windows',
      render: (_: unknown, c: CompanyItem) => (
        <Text>
          {c.jobsLast30Days} / {c.jobsLast60Days} / {c.jobsLast90Days} (Total: {c.totalStoredJobs})
        </Text>
      ),
    },
    {
      title: 'Observed Roles (from your stored jobs)',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) =>
        roles.length === 0 ? (
          <Text type="secondary">No stored listings yet</Text>
        ) : (
          <Space wrap>
            {roles.map((r) => (
              <Tag key={r}>{r}</Tag>
            ))}
          </Space>
        ),
    },
    {
      title: 'Observed Locations',
      dataIndex: 'locations',
      key: 'locations',
      render: (locs: string[]) =>
        locs.length === 0 ? (
          <Text type="secondary">—</Text>
        ) : (
          <Space wrap>
            {locs.map((l) => (
              <Tag key={l} color="purple">
                {l}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: 'Disclosed Salary Evidence',
      dataIndex: 'disclosedSalaryRange',
      key: 'disclosedSalaryRange',
    },
  ]

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>
            <BankOutlined /> Companies & Hiring Intelligence (Lite)
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Organize target companies by T1/T2/T3 tiers and priority list, and inspect 30/60/90-day evidence from your own stored jobs.
          </Paragraph>
        </Col>
        <Col>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            Add Target Company
          </Button>
        </Col>
      </Row>

      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="Listings are not hires"
        description={
          summary?.disclaimer ||
          'Listings are not hires — all 30/60/90-day counts, roles, locations, and disclosed salary ranges below are computed strictly from your own stored job listings.'
        }
      />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title="Tracked Companies"
              value={summary?.totalTrackedCompanies || 0}
              prefix={<BankOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title="Priority Companies"
              value={summary?.priorityCompaniesCount || 0}
              prefix={<StarFilled style={{ color: '#faad14' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title="Stored Job Listings Evidence"
              value={summary?.totalStoredJobsAcrossCompanies || 0}
              prefix={<BarChartOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            allowClear
            placeholder="Filter by Tier (T1/T2/T3)"
            style={{ width: 220 }}
            value={tierFilter || undefined}
            onChange={(val) => setTierFilter(val || '')}
            options={TIER_OPTIONS}
          />
          <Space>
            <Switch checked={priorityOnly} onChange={(v) => setPriorityOnly(v)} />
            <Text>Priority List Only</Text>
          </Space>
        </Space>

        <Tabs
          items={[
            {
              key: 'companies',
              label: `Target Companies (${companies.length})`,
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={companies}
                  columns={companyColumns}
                  pagination={{ pageSize: 10 }}
                />
              ),
            },
            {
              key: 'intelligence',
              label: 'Hiring Intelligence (Lite — 30/60/90d Evidence)',
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={companies}
                  columns={intelligenceColumns}
                  pagination={{ pageSize: 10 }}
                />
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="Add / Update Target Company"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText="Save Company"
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateOrUpdate}
          initialValues={{ tier: 'T1', priority: true }}
        >
          <Form.Item name="name" label="Company Name" rules={[{ required: true }]}>
            <Input placeholder="e.g. Razorpay, Zerodha, Apollo Hospitals" />
          </Form.Item>
          <Form.Item name="tier" label="Company Tier (T1 / T2 / T3)" rules={[{ required: true }]}>
            <Select options={TIER_OPTIONS} />
          </Form.Item>
          <Form.Item name="priority" label="Add to Priority List" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="website" label="Careers / Company Website">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} placeholder="Why you are targeting this company..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

