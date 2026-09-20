package org.ticketstampede.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.ticketstampede.dto.ResetRequest;
import org.ticketstampede.dto.ResetResponse;
import org.ticketstampede.entity.SaleVersion;
import org.ticketstampede.entity.Ticket;
import org.ticketstampede.infrastructure.PostgresAdvisoryLockService;
import org.ticketstampede.repository.SaleVersionRepository;
import org.ticketstampede.repository.TicketRepository;

import java.util.Optional;

@Service
public class SaleVersionService {
    private final SaleVersionRepository saleVersionRepository;
    private final TicketRepository ticketRepository;
    private final PostgresAdvisoryLockService postgresAdvisoryLockService;

    public SaleVersionService(SaleVersionRepository saleVersionRepository,
                              TicketRepository ticketRepository,
                              PostgresAdvisoryLockService postgresAdvisoryLockService)
    {
        this.saleVersionRepository = saleVersionRepository;
        this.ticketRepository = ticketRepository;
        this.postgresAdvisoryLockService = postgresAdvisoryLockService;
    }

    @Transactional
    public ResetResponse createSaleVersion(int capacity)
    {
        if(capacity<=0) throw new IllegalArgumentException("Capacity mut be positive");
        postgresAdvisoryLockService.acquireResetLock();
        SaleVersion saleVersion = new SaleVersion(capacity);
        saleVersionRepository.save(saleVersion);
        for(int ticketNumber=1; ticketNumber<=capacity; ticketNumber++)
        {
            Ticket ticket = new Ticket(saleVersion,ticketNumber);
            ticketRepository.save(ticket);
        }
        Optional<SaleVersion> previousSaleVersion = saleVersionRepository.findByActiveTrue();
        if(previousSaleVersion.isPresent())
        {
            previousSaleVersion.get().deactivate();
            saleVersionRepository.flush();
        }
        saleVersion.activate();
        return new ResetResponse(
                saleVersion.getId(),
                saleVersion.getCapacity(),
                saleVersion.getStartedAt());
    }

}
