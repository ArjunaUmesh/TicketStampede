package org.ticketstampede.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketstampede.entity.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation,UUID> {

    //in order to fetch the purchase request that maps to a reservation
    Optional<Reservation> findByPurchaseRequestId(UUID purchaseRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.id = :id
    """)
    Optional<Reservation> findByIdForUpdate(
            @Param("id") UUID id
    );
}
