package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.Vessel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VesselRepository extends JpaRepository<Vessel, Long> {
    Optional<Vessel> findByCode(String code);
}
