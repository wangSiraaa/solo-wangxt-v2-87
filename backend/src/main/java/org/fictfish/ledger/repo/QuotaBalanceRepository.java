package org.fictfish.ledger.repo;

import jakarta.persistence.LockModeType;
import org.fictfish.ledger.domain.QuotaBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface QuotaBalanceRepository extends JpaRepository<QuotaBalance, Long> {

    Optional<QuotaBalance> findByVesselIdAndSpeciesIdAndAreaIdAndSeasonId(
            Long vesselId, Long speciesId, Long areaId, Long seasonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from QuotaBalance b where b.id = :id")
    Optional<QuotaBalance> lockById(@Param("id") Long id);

    @Query("""
            select b from QuotaBalance b
            where (:vesselId is null or b.vesselId = :vesselId)
              and (:speciesId is null or b.speciesId = :speciesId)
              and (:areaId is null or b.areaId = :areaId)
              and (:seasonId is null or b.seasonId = :seasonId)
            order by b.vesselId, b.speciesId, b.areaId, b.seasonId
            """)
    List<QuotaBalance> search(@Param("vesselId") Long vesselId,
                              @Param("speciesId") Long speciesId,
                              @Param("areaId") Long areaId,
                              @Param("seasonId") Long seasonId);
}
