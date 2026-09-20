package org.ticketstampede.dto;

public record WorkloadResult(
        int ticketCapacity,
        int buyerCount,
        int concurrency,
        int httpRequestsCount,
        int errors,
        double durationSeconds,
        double throughput,
        double p50Millis,
        double p99Millis,
        boolean invariantsPassed
) {}
