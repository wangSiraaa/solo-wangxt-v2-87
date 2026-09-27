package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.Species;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpeciesRepository extends JpaRepository<Species, Long> {
    Optional<Species> findByCode(String code);
}
