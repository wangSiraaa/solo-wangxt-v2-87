package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.SeaArea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeaAreaRepository extends JpaRepository<SeaArea, Long> {
    Optional<SeaArea> findByCode(String code);
}
