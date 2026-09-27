package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.LandingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LandingItemRepository extends JpaRepository<LandingItem, Long> {
    List<LandingItem> findByVoyageItemIdIn(List<Long> voyageItemIds);
}
