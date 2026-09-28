package org.ticketstampede.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketstampede.entity.BuyerQueueEntry;

import java.util.Optional;
import java.util.UUID;

public interface BuyerQueueRepository extends JpaRepository<BuyerQueueEntry, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM BuyerQueueEntry b
            WHERE b.id = :id
           """)
    Optional<BuyerQueueEntry> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
            SELECT b.*
            FROM buyer_queue b
            JOIN purchase_request p
                ON p.id = b.purchase_request_id
            WHERE b.status = 'ACTIVE'
              AND b.expires_at > CURRENT_TIMESTAMP
              AND p.sale_version_id = :saleVersionId
            ORDER BY b.created_at ASC, b.id ASC
            LIMIT 1
            FOR UPDATE OF b SKIP LOCKED
            """,
            nativeQuery = true
    )
    Optional<BuyerQueueEntry> findFirstEligibleQueueEntry(@Param("saleVersionId") UUID saleVersionId);

}
