package org.ticketstampede.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketstampede.entity.PurchaseRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

    Optional<PurchaseRequest> findByRequestId(UUID requestId);

    @Modifying
    @Query(value = """
                    INSERT INTO purchase_request(
                        id,
                        request_id,
                        user_id,
                        status,
                        created_at                                                                                                    
                        )
                    VALUES(
                        :id,
                        :request_id,
                        :user_id,
                        'PROCESSING',
                        :created_at
                        )
                    ON CONFLICT(request_id) DO NOTHING
                    """,
    nativeQuery = true
    )
    //returns 1 if row gets inserted else 0
    int tryInsertProcessing(@Param("id") UUID id,
                            @Param("request_id") UUID requestId,
                            @Param("user_id") String userId,
                            @Param("created_at") Instant createdAt);

}
