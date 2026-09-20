package org.ticketstampede.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.ticketstampede.dto.StatusResponse;
import org.ticketstampede.dto.TicketHolderResponse;
import org.ticketstampede.entity.SaleVersion;
import org.ticketstampede.entity.Ticket;
import org.ticketstampede.entity.TicketStatus;
import org.ticketstampede.exception.NoActiveSaleException;
import org.ticketstampede.repository.SaleVersionRepository;
import org.ticketstampede.repository.TicketRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatusService {
    private final SaleVersionRepository saleVersionRepository;
    private final TicketRepository ticketRepository;

    public StatusService(SaleVersionRepository saleVersionRepository,TicketRepository ticketRepository)
    {
        this.saleVersionRepository = saleVersionRepository;
        this.ticketRepository = ticketRepository;
    }

    // REPEATABLE_READ gives /status a consistent database snapshot across its queries.
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public StatusResponse getStatus()
    {
        SaleVersion activeSaleVersion = saleVersionRepository.findByActiveTrue().orElseThrow(NoActiveSaleException::new);
        List<Ticket> soldTickets = ticketRepository.findBySaleVersionIdAndStatusOrderByTicketNumberAsc(activeSaleVersion.getId(), TicketStatus.SOLD);

        List<TicketHolderResponse> ticketHolders = new ArrayList<>();
        for(Ticket ticket : soldTickets)
        {
            ticketHolders.add(new TicketHolderResponse(ticket.getTicketNumber(),ticket.getHolderUserId()));
        }
        return new StatusResponse(activeSaleVersion.getId(),
                activeSaleVersion.getCapacity(),
                soldTickets.size(),
                ticketHolders);
    }

}
