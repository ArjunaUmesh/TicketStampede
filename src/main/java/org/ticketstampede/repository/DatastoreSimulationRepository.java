package org.ticketstampede.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.ticketstampede.entity.Ticket;

import java.util.UUID;

public interface DatastoreSimulationRepository extends Repository<Ticket, UUID> {

    @Query(value = "SELECT pg_sleep(:seconds)", nativeQuery = true)
    void sleep(@Param("seconds") double seconds);
}
