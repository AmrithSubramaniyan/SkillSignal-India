package in.skillsignal.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.*;

@Service
public class CareerService {

    public static final String PRACTICE_DISCLAIMER =
            "Practice and preparation feedback only — never predicts or claims a real interview outcome.";

    private static final Map<String, List<String>> TOPIC_SETS = new LinkedHashMap<>();

    static {
        TOPIC_SETS.put("Java", List.of(
                "Explain JVM memory areas (Heap, Metaspace, Stack) and G1GC garbage collection phases.",
                "How does ConcurrentHashMap achieve thread-safety in Java 17 compared to synchronized collections?",
                "What are Java Records and Sealed Classes, and when should you use them in domain modelling?"
        ));
        TOPIC_SETS.put("Spring Boot", List.of(
                "How does Spring Boot auto-configuration work under the hood (@ConditionalOnClass, AutoConfiguration.imports)?",
                "Explain @Transactional propagation levels (REQUIRED vs REQUIRES_NEW) and rollback rules.",
                "How do you secure REST APIs with Spring Security filter chains and stateless JWT authentication?"
        ));
        TOPIC_SETS.put("SQL", List.of(
                "Explain composite B-Tree index ordering and why leading columns matter in WHERE/ORDER BY clauses.",
                "Compare READ COMMITTED, REPEATABLE READ, and SERIALIZABLE isolation levels in PostgreSQL.",
                "Write a query using window functions (ROW_NUMBER / RANK) to find the top 2 salaries per department."
        ));
        TOPIC_SETS.put("Microservices", List.of(
                "How does the Transactional Outbox pattern prevent dual-write inconsistencies between DB and message brokers?",
                "Compare Circuit Breaker, Bulkhead, and Retry with exponential backoff + jitter.",
                "How do you design idempotent APIs using idempotency keys and row-level locks?"
        ));
        TOPIC_SETS.put("Kafka", List.of(
                "Explain Kafka consumer groups, partition assignment, and how rebalance impacts message ordering.",
                "What is the difference between at-least-once, at-most-once, and idempotent producer delivery semantics?",
                "How do you handle poison-pill messages using Dead Letter Topics (DLT)?"
        ));
        TOPIC_SETS.put("DSA", List.of(
                "When should you use a Monotonic Deque/Stack for sliding window problems (O(N) vs O(N log K))?",
                "Compare Dijkstra's shortest path algorithm with BFS on unweighted graphs.",
                "How do you detect cycles and compute topological sort in a Directed Acyclic Graph (DAG)?"
        ));
    }

    private final SkillRoadmapItemRepository roadmapRepository;
    private final InterviewPrepSessionRepository prepSessionRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Map<String, String>>> learningLinksConfig;

    public CareerService(
            SkillRoadmapItemRepository roadmapRepository,
            InterviewPrepSessionRepository prepSessionRepository,
            JobMatchRepository jobMatchRepository,
            JobRepository jobRepository,
            ObjectMapper objectMapper
    ) {
        this.roadmapRepository = roadmapRepository;
        this.prepSessionRepository = prepSessionRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.learningLinksConfig = loadLearningLinksConfig(objectMapper);
    }

    private Map<String, List<Map<String, String>>> loadLearningLinksConfig(ObjectMapper mapper) {
        try (InputStream is = new ClassPathResource("seed/learning-links.json").getInputStream()) {
            return mapper.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("DEFAULT", List.of(Map.of("title", "Open Source Roadmap", "url", "https://roadmap.sh/")));
        }
    }

    // =========================================================================
    // STAGE 31: SKILL ROADMAP (G4)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getRoadmap(UUID userId) {
        List<SkillRoadmapItem> items = roadmapRepository.findByUserIdOrderByWeekNumberAscSkillNameAsc(userId);
        long completedCount = items.stream().filter(SkillRoadmapItem::isCompleted).count();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalItems", items.size());
        res.put("completedItems", completedCount);
        res.put("items", items);
        return res;
    }

    @Transactional
    public List<SkillRoadmapItem> generateWeeklyRoadmapFromMissingSkills(UUID userId, List<String> explicitSkills) {
        Set<String> missingSkills = new LinkedHashSet<>();
        if (explicitSkills != null && !explicitSkills.isEmpty()) {
            for (String s : explicitSkills) {
                if (s != null && !s.isBlank()) missingSkills.add(s.trim());
            }
        }

        // Also aggregate missing skills from stored job matches
        List<JobMatch> matches = jobMatchRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (JobMatch m : matches) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(m.getResultJson(), new TypeReference<>() {});
                Object missingObj = parsed.get("missingRequiredSkills");
                if (missingObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o != null && !o.toString().isBlank()) missingSkills.add(o.toString().trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (missingSkills.isEmpty()) {
            missingSkills.addAll(List.of("Kafka", "Microservices", "SQL"));
        }

        List<SkillRoadmapItem> existing = roadmapRepository.findByUserIdOrderByWeekNumberAscSkillNameAsc(userId);
        Set<String> existingNames = new HashSet<>();
        for (SkillRoadmapItem item : existing) {
            existingNames.add(item.getSkillName().toLowerCase(Locale.ROOT));
        }

        int week = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getWeekNumber() + 1;
        for (String skill : missingSkills) {
            if (existingNames.contains(skill.toLowerCase(Locale.ROOT))) continue;
            SkillRoadmapItem item = new SkillRoadmapItem();
            item.setUserId(userId);
            item.setSkillName(skill);
            item.setWeekNumber(week++);
            item.setCompleted(false);
            List<Map<String, String>> links = resolveLinksForSkill(skill);
            try {
                item.setLearningLinksJson(objectMapper.writeValueAsString(links));
            } catch (Exception e) {
                item.setLearningLinksJson("[]");
            }
            item.setNotes("Week " + item.getWeekNumber() + " focus: build hands-on practice for missing skill '" + skill + "'");
            roadmapRepository.save(item);
        }

        return roadmapRepository.findByUserIdOrderByWeekNumberAscSkillNameAsc(userId);
    }

    @Transactional
    public SkillRoadmapItem toggleRoadmapCheckbox(UUID userId, UUID itemId, Boolean completed) {
        SkillRoadmapItem item = roadmapRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap item not found"));
        item.setCompleted(completed != null ? completed : !item.isCompleted());
        return roadmapRepository.save(item);
    }

    private List<Map<String, String>> resolveLinksForSkill(String skill) {
        for (var entry : learningLinksConfig.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(skill)) {
                return entry.getValue();
            }
        }
        return learningLinksConfig.getOrDefault("DEFAULT", List.of());
    }

    // =========================================================================
    // STAGE 31: INTERVIEW PREP & MOCK INTERVIEW (G5, G6)
    // =========================================================================

    public Map<String, Object> getTopicSets(String customFieldOrTopic) {
        Map<String, List<String>> topics = new LinkedHashMap<>(TOPIC_SETS);
        if (customFieldOrTopic != null && !customFieldOrTopic.isBlank()) {
            String key = customFieldOrTopic.trim();
            if (!topics.containsKey(key)) {
                topics.put(key, List.of(
                        "Explain the core domain workflows, quality standards, and key metrics in " + key + ".",
                        "Walk through a complex problem you diagnosed and resolved in " + key + " using measurable evidence.",
                        "How do you collaborate across stakeholders and handle trade-offs in " + key + " projects?"
                ));
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("disclaimer", PRACTICE_DISCLAIMER);
        res.put("topicSets", topics);
        res.put("behaviouralQuestions", List.of(
                "Tell me about a time you disagreed with a technical or process decision and how you aligned the team.",
                "Describe a high-pressure production incident or tight deadline and your exact personal contribution.",
                "Give an example of when you identified a gap in requirements before implementation started."
        ));
        res.put("projectQuestions", List.of(
                "Walk through the architecture of the most impactful project on your verified resume.",
                "What were the biggest scalability, reliability, or data-integrity trade-offs in that project?",
                "If you rebuilt that project today, what would you change and why?"
        ));
        return res;
    }

    @Transactional
    public Map<String, Object> generatePrepFromJob(UUID userId, UUID jobId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        List<String> reqSkills = new ArrayList<>();
        try {
            reqSkills = objectMapper.readValue(job.getRequiredSkillsJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
        }
        if (reqSkills.isEmpty()) reqSkills = List.of("Java", "Spring Boot", "SQL");

        List<String> technicalQuestions = new ArrayList<>();
        for (String skill : reqSkills) {
            List<String> set = TOPIC_SETS.get(skill);
            if (set != null && !set.isEmpty()) {
                technicalQuestions.addAll(set);
            } else {
                technicalQuestions.add("Explain how you have applied " + skill + " in production and how it fits the " + job.getTitle() + " role at " + job.getCompany() + ".");
            }
        }

        Map<String, Object> prep = new LinkedHashMap<>();
        prep.put("jobId", job.getId());
        prep.put("jobTitle", job.getTitle());
        prep.put("company", job.getCompany());
        prep.put("requiredSkills", reqSkills);
        prep.put("technicalQuestions", technicalQuestions);
        prep.put("behaviouralQuestions", getTopicSets(null).get("behaviouralQuestions"));
        prep.put("projectQuestions", getTopicSets(null).get("projectQuestions"));
        prep.put("disclaimer", PRACTICE_DISCLAIMER);
        return prep;
    }

    @Transactional
    public Map<String, Object> evaluateTextMockInterview(
            UUID userId,
            UUID jobId,
            String topic,
            String question,
            String userAnswer
    ) {
        String normTopic = (topic == null || topic.isBlank()) ? "Java" : topic.trim();
        String answer = userAnswer != null ? userAnswer.trim() : "";

        // Deterministic rubric evaluation based on structure, technical depth, and concrete examples
        int wordCount = answer.isBlank() ? 0 : answer.split("\\s+").length;
        boolean hasExample = answer.toLowerCase(Locale.ROOT).contains("example")
                || answer.toLowerCase(Locale.ROOT).contains("project")
                || answer.toLowerCase(Locale.ROOT).contains("implemented")
                || answer.toLowerCase(Locale.ROOT).contains("measured");
        boolean hasTechnicalTerms = answer.length() > 80;

        int clarityScore = wordCount >= 30 ? 85 : (wordCount >= 10 ? 65 : 35);
        int depthScore = hasTechnicalTerms ? 80 : 45;
        int examplesScore = hasExample ? 85 : 50;
        int overallScore = Math.round((clarityScore + depthScore + examplesScore) / 3.0f);

        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        List<String> weakTopics = new ArrayList<>();

        if (wordCount >= 30) {
            strengths.add("Clear, structured explanation with sufficient detail.");
        } else {
            improvements.add("Expand your answer with concrete architectural context and trade-offs (aim for 50–120 words).");
            weakTopics.add(normTopic + " — Depth & Completeness");
        }
        if (hasExample) {
            strengths.add("Included concrete implementation or project evidence.");
        } else {
            improvements.add("Add a specific project example (STAR format: Situation, Task, Action, Result).");
            weakTopics.add(normTopic + " — Applied Project Examples");
        }

        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("clarityScore", clarityScore);
        rubric.put("technicalDepthScore", depthScore);
        rubric.put("examplesScore", examplesScore);
        rubric.put("overallScore", overallScore);
        rubric.put("strengths", strengths);
        rubric.put("improvements", improvements);
        rubric.put("disclaimer", PRACTICE_DISCLAIMER);

        InterviewPrepSession session = new InterviewPrepSession();
        session.setUserId(userId);
        session.setJobId(jobId);
        session.setTopic(normTopic);
        session.setSessionType("MOCK_INTERVIEW");
        try {
            session.setQuestionsJson(objectMapper.writeValueAsString(List.of(question != null ? question : "Explain core concepts of " + normTopic)));
            session.setRubricFeedbackJson(objectMapper.writeValueAsString(rubric));
            session.setWeakTopicsJson(objectMapper.writeValueAsString(weakTopics));
        } catch (Exception ignored) {
        }
        session.setUserAnswer(answer);
        session.setScore(overallScore);
        session = prepSessionRepository.save(session);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sessionId", session.getId());
        res.put("topic", normTopic);
        res.put("score", overallScore);
        res.put("rubricFeedback", rubric);
        res.put("weakTopics", weakTopics);
        res.put("disclaimer", PRACTICE_DISCLAIMER);
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWeakTopicsAndSessions(UUID userId) {
        List<InterviewPrepSession> sessions = prepSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<String> aggregatedWeakTopics = new LinkedHashSet<>();
        for (InterviewPrepSession s : sessions) {
            try {
                List<String> wt = objectMapper.readValue(s.getWeakTopicsJson(), new TypeReference<>() {});
                aggregatedWeakTopics.addAll(wt);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("disclaimer", PRACTICE_DISCLAIMER);
        res.put("weakTopics", aggregatedWeakTopics);
        res.put("sessions", sessions);
        return res;
    }
}
