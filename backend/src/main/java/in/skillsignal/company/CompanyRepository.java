package in.skillsignal.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findByUserIdOrderByPriorityDescNameAsc(UUID userId);
    Optional<Company> findByIdAndUserId(UUID id, UUID userId);
    Optional<Company> findByUserIdAndNameIgnoreCase(UUID userId, String name);
    void deleteByUserId(UUID userId);
}

