package in.skillsignal.career;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_prep_sessions")
public class InterviewPrepSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "session_type", nullable = false, length = 64)
    private String sessionType = "MOCK_INTERVIEW";

    @Column(name = "questions_json", nullable = false, columnDefinition = "TEXT")
    private String questionsJson = "[]";

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "rubric_feedback_json", columnDefinition = "TEXT")
    private String rubricFeedbackJson;

    @Column(name = "weak_topics_json", columnDefinition = "TEXT")
    private String weakTopicsJson = "[]";

    @Column(name = "score")
    private Integer score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }

    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }

    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }

    public String getRubricFeedbackJson() { return rubricFeedbackJson; }
    public void setRubricFeedbackJson(String rubricFeedbackJson) { this.rubricFeedbackJson = rubricFeedbackJson; }

    public String getWeakTopicsJson() { return weakTopicsJson; }
    public void setWeakTopicsJson(String weakTopicsJson) { this.weakTopicsJson = weakTopicsJson; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

