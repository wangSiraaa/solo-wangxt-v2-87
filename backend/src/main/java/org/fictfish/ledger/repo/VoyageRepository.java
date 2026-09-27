package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.Voyage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface VoyageRepository extends JpaRepository<Voyage, Long> {
    Optional<Voyage> findByVoyageNo(String voyageNo);

    @EntityGraph(attributePaths = "items")
    @Query("select v from Voyage v order by v.departedAt desc, v.id desc")
    List<Voyage> findAllWithItems();

    @EntityGraph(attributePaths = "items")
    Optional<Voyage> findWithItemsById(Long id);
}
