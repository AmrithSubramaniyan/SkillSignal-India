import React, { useState } from 'react'
import { Card, Tabs, Form, Input, Button, Alert, Typography, Space, message } from 'antd'
import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiClient } from '../api/client'

const { Title, Paragraph, Text } = Typography

export default function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [resetSent, setResetSent] = useState<string | null>(null)

  const onLogin = async (values: { email: string; password: string }) => {
    setError(null)
    setSubmitting(true)
    try {
      await login(values.email, values.password)
      message.success('Welcome back to SkillSignal India!')
      navigate('/')
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Login failed. Please check your credentials.')
    } finally {
      setSubmitting(false)
    }
  }

  const onRegister = async (values: { fullName: string; email: string; password: string }) => {
    setError(null)
    setSubmitting(true)
    try {
      await register(values.email, values.password, values.fullName)
      message.success('Account created successfully!')
      navigate('/profile')
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Registration failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const onForgotPassword = async (values: { email: string }) => {
    setError(null)
    setResetSent(null)
    setSubmitting(true)
    try {
      const res = await apiClient.post<{ message: string }>('/auth/forgot-password', {
        email: values.email,
      })
      setResetSent(res.data.message)
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Unable to process request.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ maxWidth: 460, margin: '48px auto', padding: '0 16px' }}>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <Title level={3} style={{ marginBottom: 4 }}>
              SkillSignal India
            </Title>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              Search once. Find relevant jobs. Apply smarter. Track every application.
            </Paragraph>
          </div>

          {error && <Alert type="error" showIcon message={error} closable onClose={() => setError(null)} />}
          {resetSent && <Alert type="success" showIcon message={resetSent} />}

          <Tabs
            defaultActiveKey="login"
            items={[
              {
                key: 'login',
                label: 'Sign In',
                children: (
                  <Form layout="vertical" onFinish={onLogin}>
                    <Form.Item
                      name="email"
                      label="Email"
                      rules={[{ required: true, type: 'email', message: 'Enter a valid email' }]}
                    >
                      <Input prefix={<MailOutlined />} placeholder="demo@skillsignal.test" />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      label="Password"
                      rules={[{ required: true, message: 'Enter your password' }]}
                    >
                      <Input.Password prefix={<LockOutlined />} placeholder="Password" />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" block loading={submitting}>
                      Sign In
                    </Button>
                    <div style={{ marginTop: 12, textAlign: 'center' }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Demo account (when APP_SEED_DEMO=true): demo@skillsignal.test / DemoPass123!
                      </Text>
                    </div>
                  </Form>
                ),
              },
              {
                key: 'register',
                label: 'Create Account',
                children: (
                  <Form layout="vertical" onFinish={onRegister}>
                    <Form.Item
                      name="fullName"
                      label="Full Name"
                      rules={[{ required: true, min: 2, message: 'Enter your full name' }]}
                    >
                      <Input prefix={<UserOutlined />} placeholder="Aarav Mehta" />
                    </Form.Item>
                    <Form.Item
                      name="email"
                      label="Email"
                      rules={[{ required: true, type: 'email', message: 'Enter a valid email' }]}
                    >
                      <Input prefix={<MailOutlined />} placeholder="aarav@example.com" />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      label="Password (min 8 chars)"
                      rules={[{ required: true, min: 8, message: 'Password must be at least 8 characters' }]}
                    >
                      <Input.Password prefix={<LockOutlined />} placeholder="At least 8 characters" />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" block loading={submitting}>
                      Register
                    </Button>
                  </Form>
                ),
              },
              {
                key: 'reset',
                label: 'Reset Password',
                children: (
                  <Form layout="vertical" onFinish={onForgotPassword}>
                    <Form.Item
                      name="email"
                      label="Account Email"
                      rules={[{ required: true, type: 'email', message: 'Enter your registered email' }]}
                    >
                      <Input prefix={<MailOutlined />} placeholder="Enter your email" />
                    </Form.Item>
                    <Button htmlType="submit" block loading={submitting}>
                      Send Reset Link
                    </Button>
                  </Form>
                ),
              },
            ]}
          />
        </Space>
      </Card>
    </div>
  )
}

