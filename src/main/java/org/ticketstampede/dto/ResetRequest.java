package org.ticketstampede.dto;

import jakarta.validation.constraints.Positive;

public record ResetRequest(@Positive int capacity)
{}

