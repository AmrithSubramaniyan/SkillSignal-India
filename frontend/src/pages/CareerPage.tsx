import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Input,
  List,
  Row,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import { BookOutlined, CompassOutlined, MessageOutlined, PlusOutlined } from '@ant-design/icons'
import { apiClient } from '../api/client'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

export default function CareerPage() {
  const [roadmap, setRoadmap] = useState<any>({ items: [], totalItems: 0, completedItems: 0 })
  const [topicsData, setTopicsData] = useState<any>({})
  const [sessionsData, setSessionsData] = useState<any>({ weakTopics: [], sessions: [] })
  const [selectedTopic, setSelectedTopic] = useState('Java')
  const [customTopic, setCustomTopic] = useState('')
  const [mockAnswer, setMockAnswer] = useState('')
  const [lastFeedback, setLastFeedback] = useState<any>(null)

  const loadAll = async () => {
    try {
      const [rmRes, tpRes, msRes] = await Promise.all([
        apiClient.get('/api/career/roadmap'),
        apiClient.get('/api/career/interview-prep/topics'),
        apiClient.get('/api/career/mock-interview/sessions'),
      ])
      setRoadmap(rmRes.data)
      setTopicsData(tpRes.data)
      setSessionsData(msRes.data)
    } catch {
      message.error('Failed to load Career Roadmap & Interview Prep')
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const generateRoadmap = async () => {
    try {
      await apiClient.post('/api/career/roadmap/generate', {})
      message.success('Weekly Skill Roadmap generated from missing skills')
      loadAll()
    } catch {
      message.error('Failed to generate Skill Roadmap')
    }
  }

  const toggleItem = async (id: string, completed: boolean) => {
    try {
      await apiClient.put(`/api/career/roadmap/${id}/toggle`, { completed })
      loadAll()
    } catch {
      message.error('Failed to update progress')
    }
  }

  const addCustomTopicSet = async () => {
    if (!customTopic.trim()) return
    try {
      const res = await apiClient.get('/api/career/interview-prep/topics', {
        params: { customTopic: customTopic.trim() },
      })
      setTopicsData(res.data)
      setSelectedTopic(customTopic.trim())
      setCustomTopic('')
    } catch {
      message.error('Failed to load custom topic set')
    }
  }

  const submitMockInterview = async () => {
    const questions = topicsData?.topicSets?.[selectedTopic] || []
    const q = questions[0] || `Explain core concepts of ${selectedTopic}`
    try {
      const res = await apiClient.post('/api/career/mock-interview', {
        topic: selectedTopic,
        question: q,
        userAnswer: mockAnswer,
      })
      setLastFeedback(res.data)
      setMockAnswer('')
      message.success('Mock interview evaluated with rubric feedback')
      loadAll()
    } catch {
      message.error('Failed to evaluate mock interview')
    }
  }

  const parseLinks = (jsonStr: string) => {
    try {
      return JSON.parse(jsonStr || '[]')
    } catch {
      return []
    }
  }

  const topicSets = topicsData?.topicSets || {}

  return (
    <div style={{ maxWidth: 1280, margin: '24px auto', padding: '0 16px' }}>
      <Title level={2}>
        <CompassOutlined /> Skill Roadmap & Interview Preparation
      </Title>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          topicsData?.disclaimer ||
          'Practice and preparation feedback only — never predicts or claims a real interview outcome.'
        }
      />

      <Tabs
        items={[
          {
            key: 'roadmap',
            label: (
              <span>
                <BookOutlined /> Weekly Skill Roadmap ({roadmap.completedItems || 0}/
                {roadmap.totalItems || 0})
              </span>
            ),
            children: (
              <Card
                title="Weekly Plan from Missing Skills"
                extra={
                  <Button type="primary" icon={<PlusOutlined />} onClick={generateRoadmap}>
                    Generate / Refresh Weekly Plan from Missing Skills
                  </Button>
                }
              >
                <List
                  dataSource={roadmap.items || []}
                  renderItem={(item: any) => {
                    const links = parseLinks(item.learningLinksJson)
                    return (
                      <List.Item>
                        <div style={{ width: '100%' }}>
                          <Space>
                            <Checkbox
                              checked={item.completed}
                              onChange={(e) => toggleItem(item.id, e.target.checked)}
                            />
                            <Tag color="blue">Week {item.weekNumber}</Tag>
                            <Text strong delete={item.completed}>
                              {item.skillName}
                            </Text>
                          </Space>
                          <div style={{ marginTop: 6, paddingLeft: 28 }}>
                            <Text type="secondary">{item.notes}</Text>
                            <div style={{ marginTop: 4 }}>
                              {links.map((l: any, idx: number) => (
                                <Tag key={idx} color="geekblue">
                                  <a href={l.url} target="_blank" rel="noreferrer">
                                    {l.title}
                                  </a>
                                </Tag>
                              ))}
                            </div>
                          </div>
                        </div>
                      </List.Item>
                    )
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'interview',
            label: (
              <span>
                <MessageOutlined /> Interview Preparation & Text Mock Interview
              </span>
            ),
            children: (
              <Row gutter={[16, 16]}>
                <Col xs={24} md={12}>
                  <Card title="Topic Sets (Java, Spring Boot, SQL, Microservices, Kafka, DSA + Any Field)">
                    <Space style={{ marginBottom: 12, width: '100%' }} wrap>
                      <Select
                        value={selectedTopic}
                        onChange={setSelectedTopic}
                        style={{ width: 200 }}
                        options={Object.keys(topicSets).map((k) => ({ label: k, value: k }))}
                      />
                      <Input
                        placeholder="Add any custom field/topic..."
                        value={customTopic}
                        onChange={(e) => setCustomTopic(e.target.value)}
                        style={{ width: 200 }}
                      />
                      <Button onClick={addCustomTopicSet}>Load Topic</Button>
                    </Space>
                    <Title level={5}>Technical Questions ({selectedTopic})</Title>
                    <List
                      size="small"
                      bordered
                      dataSource={topicSets[selectedTopic] || []}
                      renderItem={(q: string) => <List.Item>{q}</List.Item>}
                      style={{ marginBottom: 12 }}
                    />
                    <Title level={5}>Behavioural & Project Questions</Title>
                    <List
                      size="small"
                      bordered
                      dataSource={[
                        ...(topicsData.behaviouralQuestions || []),
                        ...(topicsData.projectQuestions || []),
                      ]}
                      renderItem={(q: string) => <List.Item>{q}</List.Item>}
                    />
                  </Card>
                </Col>
                <Col xs={24} md={12}>
                  <Card title="Text Mock Interview with Rubric Feedback & Weak-Topic Tracker">
                    <Paragraph strong>
                      Practice Question ({selectedTopic}):{' '}
                      {(topicSets[selectedTopic] || [])[0] || `Explain core concepts of ${selectedTopic}`}
                    </Paragraph>
                    <TextArea
                      rows={5}
                      value={mockAnswer}
                      onChange={(e) => setMockAnswer(e.target.value)}
                      placeholder="Write your structured answer (include technical depth and a concrete project example)..."
                      style={{ marginBottom: 12 }}
                    />
                    <Button type="primary" onClick={submitMockInterview}>
                      Submit for Rubric Feedback
                    </Button>

                    {lastFeedback && (
                      <Card
                        size="small"
                        title={`Rubric Score: ${lastFeedback.score}/100`}
                        style={{ marginTop: 16 }}
                      >
                        <p>
                          Clarity: {lastFeedback.rubricFeedback?.clarityScore} | Technical Depth:{' '}
                          {lastFeedback.rubricFeedback?.technicalDepthScore} | Examples:{' '}
                          {lastFeedback.rubricFeedback?.examplesScore}
                        </p>
                        <Text type="secondary">{lastFeedback.disclaimer}</Text>
                      </Card>
                    )}

                    <div style={{ marginTop: 16 }}>
                      <Title level={5}>Weak-Topic List</Title>
                      {(sessionsData.weakTopics || []).length === 0 ? (
                        <Text type="secondary">No weak topics flagged yet.</Text>
                      ) : (
                        (sessionsData.weakTopics || []).map((wt: string, idx: number) => (
                          <Tag color="volcano" key={idx}>
                            {wt}
                          </Tag>
                        ))
                      )}
                    </div>
                  </Card>
                </Col>
              </Row>
            ),
          },
        ]}
      />
    </div>
  )
}

