package org.ticketstampede.dto;

import java.time.Instant;
import java.util.UUID;

public record ResetResponse(UUID saleVersionId,
                            int capacity,
                            Instant startedAt)
{}
