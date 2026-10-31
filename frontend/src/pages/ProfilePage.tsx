import React, { useEffect, useState } from 'react'
import {
  Card,
  Form,
  Input,
  InputNumber,
  Select,
  Button,
  Row,
  Col,
  Typography,
  Spin,
  Alert,
  message,
} from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Paragraph } = Typography

interface CareerProfileDto {
  targetTitles: string[]
  yearsOfExperience: number
  cities: string[]
  workModes: string[]
  expectedSalary: number | null
  salaryCurrency: string
  salaryPeriod: string
  noticePeriodDays: number | null
  skills: string[]
  employmentTypes: string[]
  country: string
  languages: string[]
  timeZone: string
  updatedAt?: string
}

interface SkillSummaryDto {
  canonicalName: string
  category: string
}

const INDIAN_CITIES = [
  'Bengaluru',
  'Mumbai',
  'Hyderabad',
  'Pune',
  'Chennai',
  'Gurugram',
  'Noida',
  'Delhi NCR',
  'Kolkata',
  'Ahmedabad',
  'Kochi',
  'Jaipur',
  'Indore',
  'Coimbatore',
]

export default function ProfilePage() {
  const [form] = Form.useForm<CareerProfileDto>()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [skillCatalog, setSkillCatalog] = useState<SkillSummaryDto[]>([])

  useEffect(() => {
    Promise.all([
      apiClient.get<CareerProfileDto>('/profile'),
      apiClient.get<SkillSummaryDto[]>('/skills'),
    ])
      .then(([profRes, skillsRes]) => {
        form.setFieldsValue(profRes.data)
        setSkillCatalog(skillsRes.data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err?.response?.data?.error || 'Failed to load career profile.')
        setLoading(false)
      })
  }, [form])

  const onSave = async (values: CareerProfileDto) => {
    setError(null)
    setSaving(true)
    try {
      const res = await apiClient.put<CareerProfileDto>('/profile', values)
      form.setFieldsValue(res.data)
      message.success('Career profile saved!')
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Failed to update profile.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 80 }}>
        <Spin size="large" tip="Loading career profile..." />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 900, margin: '24px auto', padding: '0 24px' }}>
      <Card>
        <Title level={3} style={{ marginTop: 0 }}>
          Profile & Career Settings
        </Title>
        <Paragraph type="secondary">
          Configure your target roles, experience, locations, salary preferences (LPA / annual / monthly), and optional notice period.
        </Paragraph>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        <Form form={form} layout="vertical" onFinish={onSave}>
          <Row gutter={16}>
            <Col xs={24} md={16}>
              <Form.Item name="targetTitles" label="Target Job Titles">
                <Select
                  mode="tags"
                  placeholder="Type or select target titles (e.g., Backend Engineer, Financial Analyst, Registered Nurse)"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item
                name="yearsOfExperience"
                label="Total Years of Experience"
                rules={[{ required: true, message: 'Enter years of experience' }]}
              >
                <InputNumber min={0} max={60} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={24}>
              <Form.Item name="skills" label="Core Skills (Cross-Industry Catalog + Custom)">
                <Select
                  mode="tags"
                  placeholder="Select or type your skills"
                  options={skillCatalog.map((s) => ({
                    label: `${s.canonicalName} (${s.category})`,
                    value: s.canonicalName,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name="cities" label="Preferred Cities">
                <Select
                  mode="tags"
                  placeholder="Select Indian or global cities"
                  options={INDIAN_CITIES.map((c) => ({ label: c, value: c }))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="workModes" label="Work Modes">
                <Select
                  mode="multiple"
                  options={[
                    { label: 'Remote', value: 'REMOTE' },
                    { label: 'Hybrid', value: 'HYBRID' },
                    { label: 'On-site', value: 'ONSITE' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="employmentTypes" label="Employment Types">
                <Select
                  mode="multiple"
                  options={[
                    { label: 'Full-Time', value: 'FULL_TIME' },
                    { label: 'Contract', value: 'CONTRACT' },
                    { label: 'Part-Time', value: 'PART_TIME' },
                    { label: 'Internship', value: 'INTERNSHIP' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={6}>
              <Form.Item name="expectedSalary" label="Expected Salary">
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} placeholder="e.g., 18.5" />
              </Form.Item>
            </Col>
            <Col xs={24} md={5}>
              <Form.Item name="salaryCurrency" label="Currency">
                <Select
                  options={[
                    { label: 'INR (₹)', value: 'INR' },
                    { label: 'USD ($)', value: 'USD' },
                    { label: 'EUR (€)', value: 'EUR' },
                    { label: 'GBP (£)', value: 'GBP' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="salaryPeriod" label="Salary Period">
                <Select
                  options={[
                    { label: 'LPA (Lakhs Per Annum)', value: 'LPA' },
                    { label: 'Annual', value: 'ANNUAL' },
                    { label: 'Monthly', value: 'MONTHLY' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={7}>
              <Form.Item name="noticePeriodDays" label="Notice Period in Days (Optional)">
                <InputNumber min={0} max={365} style={{ width: '100%' }} placeholder="e.g., 30 (Immediate = 0)" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item name="country" label="Country">
                <Input placeholder="India" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="languages" label="Languages">
                <Select
                  mode="tags"
                  options={[
                    { label: 'English', value: 'English' },
                    { label: 'Hindi', value: 'Hindi' },
                    { label: 'Tamil', value: 'Tamil' },
                    { label: 'Telugu', value: 'Telugu' },
                    { label: 'Kannada', value: 'Kannada' },
                    { label: 'Marathi', value: 'Marathi' },
                    { label: 'Bengali', value: 'Bengali' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="timeZone" label="Time Zone">
                <Input placeholder="Asia/Kolkata" />
              </Form.Item>
            </Col>
          </Row>

          <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
            Save Profile Settings
          </Button>
        </Form>
      </Card>
    </div>
  )
}
