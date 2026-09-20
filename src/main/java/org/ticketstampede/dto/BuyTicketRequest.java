package org.ticketstampede.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuyTicketRequest (
        @NotBlank String userId,
        @NotNull UUID requestId)
{}
