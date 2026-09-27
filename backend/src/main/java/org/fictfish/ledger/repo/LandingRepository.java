package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.Landing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LandingRepository extends JpaRepository<Landing, Long> {
    Optional<Landing> findByCertificateNo(String certificateNo);

    @EntityGraph(attributePaths = "items")
    List<Landing> findByVoyageIdOrderByLandedAtAscIdAsc(Long voyageId);
}
