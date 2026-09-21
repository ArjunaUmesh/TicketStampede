package org.ticketstampede.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketstampede.entity.Ticket;
import org.ticketstampede.entity.TicketStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsBySaleVersionIdAndStatus(UUID saleVersionId, TicketStatus status);
    List<Ticket> findBySaleVersionIdAndStatusOrderByTicketNumberAsc(UUID saleVersionId,TicketStatus status);

    //find the first Ticket that is available and not locked by another transaction
    @Query(
            value = """
                    SELECT *
                    FROM ticket
                    WHERE sale_version_id = :saleVersionId
                      AND status = 'AVAILABLE'
                    ORDER BY ticket_number ASC
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<Ticket> findAvailableTicket(@Param("saleVersionId") UUID saleVersionId);

}
