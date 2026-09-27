package org.fictfish.ledger.repo;

import org.fictfish.ledger.domain.QuotaTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface QuotaTransferRepository extends JpaRepository<QuotaTransfer, Long> {

    Optional<QuotaTransfer> findByTransferNo(String transferNo);

    @Query("""
            select t from QuotaTransfer t
            where t.sourceBalanceId = :balanceId or t.destBalanceId = :balanceId
            order by t.createdAt desc, t.id desc
            """)
    List<QuotaTransfer> findInvolvingBalance(@Param("balanceId") Long balanceId);
}
