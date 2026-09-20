package org.ticketstampede.dto;

public record ErrorResponse(
        int status,
        String code,
        String description
)
{}
