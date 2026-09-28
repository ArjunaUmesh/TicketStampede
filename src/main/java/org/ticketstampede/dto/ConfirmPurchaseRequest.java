package org.ticketstampede.dto;

import jakarta.validation.constraints.NotNull;
import org.ticketstampede.entity.PurchaseRequest;

import java.util.UUID;

public record ConfirmPurchaseRequest(
        @NotNull UUID paymentId
) {
}
