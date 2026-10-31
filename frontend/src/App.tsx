import React from 'react'
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import { Layout, Menu, Tag, Spin, Space, Button } from 'antd'
import {
  LogoutOutlined,
  DashboardOutlined,
  UserOutlined,
  SettingOutlined,
  SafetyCertificateOutlined,
  FileTextOutlined,
  AimOutlined,
  SolutionOutlined,
  SearchOutlined,
  ApiOutlined,
  CalendarOutlined,
  BellOutlined,
  BankOutlined,
  SendOutlined,
  CheckSquareOutlined,
} from '@ant-design/icons'
import { AuthProvider, useAuth } from './context/AuthContext'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import ResumesPage from './pages/ResumesPage'
import JobsPage from './pages/JobsPage'
import JobSearchPage from './pages/JobSearchPage'
import SourcesPage from './pages/SourcesPage'
import TrackerPage from './pages/TrackerPage'
import RemindersPage from './pages/RemindersPage'
import NotificationsPage from './pages/NotificationsPage'
import CompaniesPage from './pages/CompaniesPage'
import AutoApplySettingsPage from './pages/AutoApplySettingsPage'
import AssistedApplyPage from './pages/AssistedApplyPage'
import ApprovalQueuePage from './pages/ApprovalQueuePage'
import AutoApplyCentrePage from './pages/AutoApplyCentrePage'
import AnalyticsPage from './pages/AnalyticsPage'
import CareerPage from './pages/CareerPage'
import ProductionPage from './pages/ProductionPage'
import ProfilePage from './pages/ProfilePage'
import PrivacyPage from './pages/PrivacyPage'

const { Header, Content } = Layout

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
                  key: '/resumes',
                  icon: <FileTextOutlined />,
                  label: <Link to="/resumes">Resumes</Link>,
                },
                {
                  key: '/search',
                  icon: <SearchOutlined />,
                  label: <Link to="/search">AI Job Search</Link>,
                },
                {
                  key: '/jobs',
                  icon: <AimOutlined />,
                  label: <Link to="/jobs">Jobs & Match</Link>,
                },
                {
                  key: '/sources',
                  icon: <ApiOutlined />,
                  label: <Link to="/sources">Sources</Link>,
                },
                {
                  key: '/tracker',
                  icon: <SolutionOutlined />,
                  label: <Link to="/tracker">Tracker</Link>,
                },
                {
                  key: '/reminders',
                  icon: <CalendarOutlined />,
                  label: <Link to="/reminders">Reminders</Link>,
                },
                {
                  key: '/notifications',
                  icon: <BellOutlined />,
                  label: <Link to="/notifications">Notifications</Link>,
                },
                {
                  key: '/companies',
                  icon: <BankOutlined />,
                  label: <Link to="/companies">Companies</Link>,
                },
                {
                  key: '/assisted-apply',
                  icon: <SendOutlined />,
                  label: <Link to="/assisted-apply">Assisted Apply</Link>,
                },
                {
                  key: '/approval-queue',
                  icon: <CheckSquareOutlined />,
                  label: <Link to="/approval-queue">Approval Queue</Link>,
                },
                {
                  key: '/auto-apply-centre',
                  icon: <SafetyCertificateOutlined />,
                  label: <Link to="/auto-apply-centre">Auto-Apply Centre</Link>,
                },
                {
                  key: '/auto-apply-settings',
                  icon: <SafetyCertificateOutlined />,
                  label: <Link to="/auto-apply-settings">Apply Rules</Link>,
                },
                {
                  key: '/analytics',
                  icon: <DashboardOutlined />,
                  label: <Link to="/analytics">Analytics</Link>,
                },
                {
                  key: '/career',
                  icon: <SolutionOutlined />,
                  label: <Link to="/career">Career & Prep</Link>,
                },
                {
                  key: '/plans',
                  icon: <SafetyCertificateOutlined />,
                  label: <Link to="/plans">Plans & Feedback</Link>,
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
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/resumes"
            element={
              <ProtectedRoute>
                <ResumesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/search"
            element={
              <ProtectedRoute>
                <JobSearchPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/jobs"
            element={
              <ProtectedRoute>
                <JobsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/sources"
            element={
              <ProtectedRoute>
                <SourcesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/tracker"
            element={
              <ProtectedRoute>
                <TrackerPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/reminders"
            element={
              <ProtectedRoute>
                <RemindersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/notifications"
            element={
              <ProtectedRoute>
                <NotificationsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/companies"
            element={
              <ProtectedRoute>
                <CompaniesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/assisted-apply"
            element={
              <ProtectedRoute>
                <AssistedApplyPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/approval-queue"
            element={
              <ProtectedRoute>
                <ApprovalQueuePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/auto-apply-centre"
            element={
              <ProtectedRoute>
                <AutoApplyCentrePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/analytics"
            element={
              <ProtectedRoute>
                <AnalyticsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/career"
            element={
              <ProtectedRoute>
                <CareerPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/plans"
            element={
              <ProtectedRoute>
                <ProductionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/auto-apply-settings"
            element={
              <ProtectedRoute>
                <AutoApplySettingsPage />
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
