package org.ticketstampede.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketstampede.entity.SaleVersion;
import java.util.Optional;
import java.util.UUID;

public interface SaleVersionRepository extends JpaRepository<SaleVersion, UUID> {

    Optional<SaleVersion> findByActiveTrue();

}
