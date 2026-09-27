package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByBalanceIdOrderByEntryDateAscIdAsc(Long balanceId);
}
