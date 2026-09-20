package org.ticketstampede.dto;

import org.ticketstampede.entity.PurchaseStatus;

import java.time.Instant;
import java.util.UUID;

public record BuyTicketResponse(
        PurchaseStatus status,
        UUID saleVersionId,
        UUID requestId,
        Integer ticketNumber,
        Instant completedAt
)
{}
