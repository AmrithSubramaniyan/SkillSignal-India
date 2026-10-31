package in.skillsignal.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdOrderByCheckedInAtDesc(UUID userId);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.id = :id AND s.userId = :userId")
    Optional<UserSession> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.id = :id")
    Optional<UserSession> findByIdForUpdate(@Param("id") UUID id);

    Optional<UserSession> findFirstByUserIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(UUID userId);

    List<UserSession> findByCheckedOutAtIsNull();

    void deleteByUserId(UUID userId);
}

