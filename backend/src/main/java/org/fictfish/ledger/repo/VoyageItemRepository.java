package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.VoyageItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoyageItemRepository extends JpaRepository<VoyageItem, Long> {
}
