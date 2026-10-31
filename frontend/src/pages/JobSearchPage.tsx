import { useEffect, useState } from 'react'
import {
  Card,
  Input,
  Button,
  Tag,
  Space,
  Typography,
  Row,
  Col,
  Select,
  Switch,
  InputNumber,
  Alert,
  List,
  Badge,
  message,
} from 'antd'
import {
  SearchOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  EnvironmentOutlined,
  DollarOutlined,
} from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { apiClient as api } from '../api/client'

const { Title, Text, Paragraph } = Typography

export interface FilterChip {
  key: string
  label: string
  value: string
}

export interface JobSearchFilters {
  rawQuery?: string
  keywords: string[]
  skills: string[]
  minExperienceYears?: number | null
  maxExperienceYears?: number | null
  locations: string[]
  workMode?: string | null
  disclosedSalaryOnly?: boolean
  minSalaryLpa?: number | null
  postedWithinDays?: number | null
  company?: string | null
  sources: string[]
  employmentType?: string | null
  savedOnly?: boolean
  appliedStatus?: string | null
  greenhouseBoards?: string[]
}

export interface SourceExecutionReport {
  source: string
  status: string
  jobsFound: number
  durationMs: number
  errorReason: string | null
}

export interface JobSearchResultItem {
  id: string
  title: string
  company: string
  location: string
  workMode: string
  source: string
  sourceUrl: string | null
  employmentType: string
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  salaryPeriod: string | null
  minYearsExperience: number | null
  requiredSkills: string[]
  niceToHaveSkills: string[]
  sourceRecords: Array<{ source: string; externalId?: string; url?: string }>
  saved: boolean
  stale: boolean
  staleReason: string | null
  applied: boolean
  postedAt: string
}

export interface JobSearchResponse {
  appliedFilters: JobSearchFilters
  filterChips: FilterChip[]
  jobs: JobSearchResultItem[]
  sourcesSearched: SourceExecutionReport[]
  sourcesFailed: SourceExecutionReport[]
  totalDurationMs: number
  coverageDisclaimer: string
}

const EMPTY_FILTERS: JobSearchFilters = {
  rawQuery: '',
  keywords: [],
  skills: [],
  locations: [],
  sources: ['manual', 'company_ats'],
  disclosedSalaryOnly: false,
  savedOnly: false,
}

export default function JobSearchPage() {
  const [queryText, setQueryText] = useState(
    'Backend developer with Java and Spring Boot in Bengaluru 3+ years hybrid above 18 LPA'
  )
  const [filters, setFilters] = useState<JobSearchFilters>(EMPTY_FILTERS)
  const [chips, setChips] = useState<FilterChip[]>([])
  const [searchResult, setSearchResult] = useState<JobSearchResponse | null>(null)
  const [loading, setLoading] = useState(false)

  const handleParseAndSearch = async () => {
    setLoading(true)
    try {
      let activeFilters: JobSearchFilters = { ...filters, rawQuery: queryText }
      if (queryText.trim()) {
        const parseRes = await api.post<{ filters: JobSearchFilters; chips: FilterChip[] }>('/jobs/parse-query', {
          query: queryText,
        })
        activeFilters = {
          ...parseRes.data.filters,
          sources:
            parseRes.data.filters.sources && parseRes.data.filters.sources.length > 0
              ? parseRes.data.filters.sources
              : ['manual', 'company_ats'],
        }
        setFilters(activeFilters)
        setChips(parseRes.data.chips)
      }
      const searchRes = await api.post<JobSearchResponse>('/jobs/search', activeFilters)
      setSearchResult(searchRes.data)
      setFilters(searchRes.data.appliedFilters)
      setChips(searchRes.data.filterChips)
    } catch (err: any) {
      message.error(err.response?.data?.error || 'Failed to run job search')
    } finally {
      setLoading(false)
    }
  }

  const runWithFilters = async (nextFilters: JobSearchFilters) => {
    setFilters(nextFilters)
    setLoading(true)
    try {
      const searchRes = await api.post<JobSearchResponse>('/jobs/search', {
        ...nextFilters,
        rawQuery: '',
      })
      setSearchResult(searchRes.data)
      setChips(searchRes.data.filterChips)
    } catch {
      message.error('Failed to update search filters')
    } finally {
      setLoading(false)
    }
  }

  const handleRemoveChip = (chip: FilterChip) => {
    const next = { ...filters }
    if (chip.key === 'keywords') next.keywords = next.keywords.filter((k) => k !== chip.value)
    else if (chip.key === 'skills') next.skills = next.skills.filter((s) => s !== chip.value)
    else if (chip.key === 'locations') next.locations = next.locations.filter((l) => l !== chip.value)
    else if (chip.key === 'experience') {
      next.minExperienceYears = null
      next.maxExperienceYears = null
    } else if (chip.key === 'workMode') next.workMode = null
    else if (chip.key === 'minSalaryLpa') {
      next.minSalaryLpa = null
      next.disclosedSalaryOnly = false
    } else if (chip.key === 'disclosedSalaryOnly') next.disclosedSalaryOnly = false
    else if (chip.key === 'postedWithinDays') next.postedWithinDays = null
    else if (chip.key === 'company') next.company = null
    else if (chip.key === 'sources') next.sources = next.sources.filter((s) => s !== chip.value)
    else if (chip.key === 'employmentType') next.employmentType = null
    else if (chip.key === 'savedOnly') next.savedOnly = false
    else if (chip.key === 'appliedStatus') next.appliedStatus = null
    runWithFilters(next)
  }

  useEffect(() => {
    runWithFilters(EMPTY_FILTERS)
  }, [])

  return (
    <div style={{ maxWidth: 1200, margin: '24px auto', padding: '0 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            <ThunderboltOutlined /> AI Job Search & Multi-Source Orchestrator
          </Title>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            Type a natural-language job query to extract editable filter chips, then search across enabled sources in parallel.
          </Paragraph>
        </div>
        <Link to="/sources">
          <Button>Connected Sources & Permissions</Button>
        </Link>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
          <Input
            size="large"
            value={queryText}
            onChange={(e) => setQueryText(e.target.value)}
            placeholder="e.g., Remote Python data scientist 4 to 7 years above 20 LPA posted last 7 days"
            onPressEnter={handleParseAndSearch}
          />
          <Button type="primary" size="large" icon={<SearchOutlined />} loading={loading} onClick={handleParseAndSearch}>
            Parse & Search
          </Button>
        </Space.Compact>

        <div style={{ marginBottom: 12 }}>
          <Text strong style={{ marginRight: 8 }}>
            Editable Filter Chips:
          </Text>
          {chips.length === 0 ? (
            <Text type="secondary">No active filter chips (showing all jobs from enabled sources)</Text>
          ) : (
            <Space wrap>
              {chips.map((chip, idx) => (
                <Tag
                  key={`${chip.key}-${chip.value}-${idx}`}
                  closable
                  color="processing"
                  onClose={(e) => {
                    e.preventDefault()
                    handleRemoveChip(chip)
                  }}
                >
                  {chip.label}
                </Tag>
              ))}
              <Button
                size="small"
                type="link"
                onClick={() => {
                  setQueryText('')
                  runWithFilters(EMPTY_FILTERS)
                }}
              >
                Clear All
              </Button>
            </Space>
          )}
        </div>

        <Row gutter={[12, 12]}>
          <Col xs={24} sm={6}>
            <Text type="secondary">Work Mode</Text>
            <Select
              allowClear
              style={{ width: '100%' }}
              placeholder="Any Work Mode"
              value={filters.workMode || undefined}
              onChange={(val) => runWithFilters({ ...filters, workMode: val || null })}
              options={[
                { value: 'REMOTE', label: 'Remote' },
                { value: 'HYBRID', label: 'Hybrid' },
                { value: 'ONSITE', label: 'Onsite' },
              ]}
            />
          </Col>
          <Col xs={24} sm={6}>
            <Text type="secondary">Employment Type</Text>
            <Select
              allowClear
              style={{ width: '100%' }}
              placeholder="Any Type"
              value={filters.employmentType || undefined}
              onChange={(val) => runWithFilters({ ...filters, employmentType: val || null })}
              options={[
                { value: 'FULL_TIME', label: 'Full-Time' },
                { value: 'CONTRACT', label: 'Contract' },
                { value: 'INTERNSHIP', label: 'Internship' },
                { value: 'PART_TIME', label: 'Part-Time' },
              ]}
            />
          </Col>
          <Col xs={24} sm={6}>
            <Text type="secondary">Min Salary (LPA)</Text>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              max={200}
              placeholder="Any Salary"
              value={filters.minSalaryLpa ?? undefined}
              onChange={(val) =>
                runWithFilters({
                  ...filters,
                  minSalaryLpa: typeof val === 'number' ? val : null,
                  disclosedSalaryOnly: typeof val === 'number' ? true : filters.disclosedSalaryOnly,
                })
              }
            />
          </Col>
          <Col xs={24} sm={6}>
            <Text type="secondary">Applied / Saved</Text>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
              <Switch
                size="small"
                checked={Boolean(filters.savedOnly)}
                onChange={(checked) => runWithFilters({ ...filters, savedOnly: checked })}
              />
              <Text>Saved Only</Text>
            </div>
          </Col>
        </Row>
      </Card>

      {searchResult && (
        <Alert
          type={searchResult.sourcesFailed.length > 0 ? 'warning' : 'success'}
          showIcon
          style={{ marginBottom: 16 }}
          message={
            <Space wrap>
              <Text strong>
                Found {searchResult.jobs.length} job(s) in {searchResult.totalDurationMs} ms
              </Text>
              {searchResult.sourcesSearched.map((s) => (
                <Tag key={s.source} icon={<CheckCircleOutlined />} color="success">
                  {s.source} ({s.jobsFound} jobs, {s.durationMs}ms)
                </Tag>
              ))}
              {searchResult.sourcesFailed.map((f) => (
                <Tag key={f.source} icon={<WarningOutlined />} color="warning">
                  {f.source}: {f.errorReason}
                </Tag>
              ))}
            </Space>
          }
          description={<Text type="secondary">{searchResult.coverageDisclaimer}</Text>}
        />
      )}

      <List
        loading={loading}
        dataSource={searchResult?.jobs || []}
        renderItem={(job) => (
          <Card key={job.id} style={{ marginBottom: 12 }} size="small">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Space wrap>
                  <Text strong style={{ fontSize: 16 }}>
                    {job.title}
                  </Text>
                  <Tag color="blue">{job.company}</Tag>
                  <Tag icon={<EnvironmentOutlined />}>{job.location || 'Unspecified'}</Tag>
                  <Tag color="purple">{job.workMode}</Tag>
                  <Tag>{job.employmentType || 'FULL_TIME'}</Tag>
                  {job.salaryMin != null && (
                    <Tag icon={<DollarOutlined />} color="gold">
                      {job.salaryMin}
                      {job.salaryMax && job.salaryMax !== job.salaryMin ? `-${job.salaryMax}` : ''}{' '}
                      {job.salaryPeriod || 'LPA'}
                    </Tag>
                  )}
                  {job.stale && <Badge status="error" text={`Stale (${job.staleReason || 'Closed'})`} />}
                  {job.saved && <Tag color="cyan">Saved</Tag>}
                  {job.applied && <Tag color="green">Applied</Tag>}
                </Space>
                <div style={{ marginTop: 8 }}>
                  <Space wrap size={[4, 4]}>
                    {(job.requiredSkills || []).map((sk) => (
                      <Tag key={sk} color="geekblue">
                        {sk}
                      </Tag>
                    ))}
                  </Space>
                </div>
                <div style={{ marginTop: 6 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Sources:{' '}
                    {job.sourceRecords && job.sourceRecords.length > 0
                      ? job.sourceRecords.map((r) => r.source).join(', ')
                      : job.source}
                  </Text>
                </div>
              </div>
              <Space>
                <Link to="/jobs">
                  <Button size="small" type="primary">
                    Match & Compare
                  </Button>
                </Link>
              </Space>
            </div>
          </Card>
        )}
      />
    </div>
  )
}
