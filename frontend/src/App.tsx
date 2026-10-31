import React, { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import { Layout, Menu, Card, Typography, Tag, Spin, Alert, Space, Button } from 'antd'
import {
  CheckCircleOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  DashboardOutlined,
  UserOutlined,
  SettingOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { AuthProvider, useAuth } from './context/AuthContext'
import { apiClient } from './api/client'
import LoginPage from './pages/LoginPage'
import ProfilePage from './pages/ProfilePage'
import PrivacyPage from './pages/PrivacyPage'

const { Header, Content } = Layout
const { Title, Paragraph, Text } = Typography

interface HealthResponse {
  status: string
  service?: string
  dbTime: string
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 120 }}>
        <Spin size="large" tip="Verifying session..." />
      </div>
    )
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

function HomeStatusView() {
  const { user } = useAuth()
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    apiClient
      .get<HealthResponse>('/health')
      .then((res) => {
        setHealth(res.data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err?.response?.data?.error || err.message || 'Unable to reach /api/health')
        setLoading(false)
      })
  }, [])

  return (
    <div style={{ maxWidth: 840, margin: '32px auto', padding: '0 24px' }}>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Title level={3} style={{ margin: 0 }}>
            Welcome, {user?.fullName || 'Job Seeker'}
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 4 }}>
            Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time.
          </Paragraph>

          <Card size="small" title="System Health (/api/health)">
            {loading && <Spin tip="Checking backend and database..." />}
            {error && <Alert type="error" showIcon message="Backend Unreachable" description={error} />}
            {health && (
              <Space wrap size="large">
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

function AppShell() {
  const { user, logout } = useAuth()
  const location = useLocation()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#001529',
          padding: '0 24px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 24, flex: 1 }}>
          <Link to="/" style={{ color: '#fff', fontWeight: 700, fontSize: 18, whiteSpace: 'nowrap' }}>
            SkillSignal India
          </Link>
          {user && (
            <Menu
              theme="dark"
              mode="horizontal"
              selectedKeys={[location.pathname]}
              style={{ flex: 1, minWidth: 0 }}
              items={[
                {
                  key: '/',
                  icon: <DashboardOutlined />,
                  label: <Link to="/">Dashboard</Link>,
                },
                {
                  key: '/profile',
                  icon: <SettingOutlined />,
                  label: <Link to="/profile">Profile & Settings</Link>,
                },
                {
                  key: '/privacy',
                  icon: <SafetyCertificateOutlined />,
                  label: <Link to="/privacy">Privacy & Consents</Link>,
                },
              ]}
            />
          )}
        </div>
        <div>
          {user ? (
            <Space>
              <Tag icon={<UserOutlined />} color="blue">
                {user.email}
              </Tag>
              <Button size="small" icon={<LogoutOutlined />} onClick={() => logout()}>
                Sign Out
              </Button>
            </Space>
          ) : (
            <Link to="/login">
              <Button type="primary" size="small">
                Sign In
              </Button>
            </Link>
          )}
        </div>
      </Header>
      <Content>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomeStatusView />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/privacy"
            element={
              <ProtectedRoute>
                <PrivacyPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Content>
    </Layout>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </BrowserRouter>
  )
}
