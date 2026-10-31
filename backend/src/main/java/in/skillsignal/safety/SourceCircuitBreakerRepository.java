package in.skillsignal.safety;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceCircuitBreakerRepository extends JpaRepository<SourceCircuitBreaker, UUID> {
    List<SourceCircuitBreaker> findByUserIdOrderBySourceAsc(UUID userId);
    Optional<SourceCircuitBreaker> findByUserIdAndSourceIgnoreCase(UUID userId, String source);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cb FROM SourceCircuitBreaker cb WHERE cb.userId = :userId AND LOWER(cb.source) = LOWER(:source)")
    Optional<SourceCircuitBreaker> findWithLockByUserIdAndSource(
            @Param("userId") UUID userId,
            @Param("source") String source
    );

    void deleteByUserId(UUID userId);
}

