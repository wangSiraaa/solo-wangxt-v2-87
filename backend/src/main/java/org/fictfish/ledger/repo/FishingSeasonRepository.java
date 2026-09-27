package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.FishingSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FishingSeasonRepository extends JpaRepository<FishingSeason, Long> {
    Optional<FishingSeason> findByCode(String code);
}
