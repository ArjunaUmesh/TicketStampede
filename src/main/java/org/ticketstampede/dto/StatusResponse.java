package org.ticketstampede.dto;

import java.util.List;
import java.util.UUID;

public record StatusResponse
        (
        UUID saleVersionId,
        int capacity,
        int soldTicketCount,
        List<TicketHolderResponse> ticketHolders
        )
{}
