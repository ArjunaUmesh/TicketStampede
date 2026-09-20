package org.ticketstampede.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.*;
import org.ticketstampede.entity.PurchaseStatus;

import java.net.URI;
import java.net.http.HttpResponse;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class WorkloadRunner {
    private final BuyerClient buyerClient;
    private final ObjectMapper objectMapper;

    public WorkloadRunner(BuyerClient buyerClient, ObjectMapper objectMapper)
    {
        this.buyerClient = buyerClient;
        this.objectMapper = objectMapper;
    }

    public WorkloadResult runBuyWorkload(int ticketCapacity, int buyerCount, int concurrency) throws JsonProcessingException {

        ResetResponse resetResponse = objectMapper.readValue(buyerClient.reset(ticketCapacity).join().body(),ResetResponse.class);
        List<BuyTicketRequest> requests = generateBuyRequests(buyerCount);
        long startTime = System.nanoTime();
        List<TimedResponse> timedResponses = executeBuyRequests(requests,concurrency);
        List<Long> latencies = timedResponses
                .stream()
                .map(timedResponse -> timedResponse.latency)
                .sorted()
                .toList();
        long endTime = System.nanoTime();
        List<BuyTicketResponse> buyTicketResponses = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        parseResponses(timedResponses,buyTicketResponses,errorResponses);
        StatusResponse statusResponse = objectMapper.readValue(buyerClient.status().join().body(),StatusResponse.class);

        System.out.println("Logical buyers: " + buyerCount);
        System.out.println("HTTP requests: " + requests.size());
        System.out.println("Errors: " + errorResponses.size());

        boolean invariantsPassed = verifyInvariants(resetResponse,buyTicketResponses,statusResponse);

        double durationSeconds = (endTime - startTime) / 1000000000.0;
        double throughput = requests.size() / durationSeconds;
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Throughput: " + throughput + " requests/sec");

        long p50Nanoseconds = percentile(latencies, 0.50);
        long p99Nanoseconds = percentile(latencies, 0.99);

        double p50Millis = p50Nanoseconds / 1000000.0;
        double p99Millis = p99Nanoseconds / 1000000.0;

        System.out.println("p50 latency: " + p50Millis + " ms");
        System.out.println("p99 latency: " + p99Millis + " ms");

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(errorResponses)
        );

        return new WorkloadResult(ticketCapacity,
                buyerCount,
                concurrency,
                requests.size(),
                errorResponses.size(),
                durationSeconds,
                throughput,
                p50Millis,
                p99Millis,
                invariantsPassed);
    }

    private List<BuyTicketRequest> generateBuyRequests(int buyerCount)
    {
        List<BuyTicketRequest> requests = new ArrayList<>();
        for (int i = 1; i <= buyerCount; i++)
        {
            UUID requestId = UUID.randomUUID();
            String userId = "user-" + i;
            requests.add(new BuyTicketRequest(userId, requestId));
            if (i % 3 == 0)
            {
                requests.add(new BuyTicketRequest(userId, requestId));
            }
        }
        return requests;
    }

    private List<TimedResponse> executeBuyRequests(List<BuyTicketRequest> requests, int concurrency) throws JsonProcessingException {
        if (concurrency <= 0)
        {
            throw new IllegalArgumentException("Concurrency must be positive");
        }
        int totalRequests = requests.size();
        List<TimedResponse> responses = new ArrayList<>();
        for(int start = 0; start < totalRequests ; start += concurrency)
        {
            List<CompletableFuture<TimedResponse>> futureList = new ArrayList<>();
            for(int i=0;i<min(concurrency,totalRequests-start);i++)
            {
                BuyTicketRequest buyTicketRequest = requests.get(start+i);
                long requestStartTime = System.nanoTime();
                futureList.add(buyerClient.buy(
                                                buyTicketRequest.requestId(),
                                                buyTicketRequest.userId()
                                            ).thenApply(response ->
                                                new TimedResponse(
                                                                response,
                                                    System.nanoTime() - requestStartTime
                                                )
                                            )
                );
            }
            for (CompletableFuture<TimedResponse> future : futureList)
            {
                responses.add(future.join());
            }
        }
        return responses;
    }

    private void parseResponses(List<TimedResponse> timedResponses,
                                List<BuyTicketResponse> buyTicketResponses,
                                List<ErrorResponse> errorResponses) throws JsonProcessingException {
        for(TimedResponse timedResponse : timedResponses)
        {
            if(timedResponse.response.statusCode()== 200)
            {
                buyTicketResponses.add(objectMapper.readValue(timedResponse.response.body(),BuyTicketResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(timedResponse.response.body(),ErrorResponse.class));
            }
        }
    }

    private boolean verifyInvariants(ResetResponse resetResponse,
                                     List<BuyTicketResponse> buyTicketResponses,
                                     StatusResponse statusResponse)
    {
        boolean invariant1,invariant2,invariant3,invariant4;
        invariant1 = invariant2 = invariant3 = invariant4 = true;

        Set<Integer> statusResponseSoldTickets = statusResponse
                .ticketHolders()
                .stream()
                .map(TicketHolderResponse::ticketNumber)
                .collect(Collectors.toSet());


        long purchasedCount = buyTicketResponses
                .stream()
                .filter(buyTicketResponse ->
                        buyTicketResponse.status() == PurchaseStatus.PURCHASED)
                .map(BuyTicketResponse::requestId)
                .collect(Collectors.toSet())
                .size();

        Set<Integer> purchasedTicketNumbers = buyTicketResponses
                .stream()
                .filter(buyTicketResponse -> buyTicketResponse.status() == PurchaseStatus.PURCHASED)
                .map(BuyTicketResponse::ticketNumber)
                .collect(Collectors.toSet());

        int purchasedTicketNumbersCount = purchasedTicketNumbers.size();

        // INVARIANT 1: Never sell more tickets than exist
        if (purchasedCount > resetResponse.capacity())
        {
            System.out.println("INVARIANT FAILED: oversold tickets");
            invariant1 = false;
        }

        // INVARIANT 2: No ticket number must be issued more than once
        if (purchasedCount != purchasedTicketNumbersCount)
        {
            System.out.println("INVARIANT FAILED: duplicate ticket number issued");
            invariant2 = false;
        }

        //INVARIANT 3 : requestId <-> ticketNumber is unique
        Map<UUID,Integer> requestIdTicketNumberMapping = new HashMap<>();
        for(BuyTicketResponse buyTicketResponse : buyTicketResponses)
        {
            if(buyTicketResponse.status() != PurchaseStatus.PURCHASED)
            {
                continue;
            }
            UUID requestId = buyTicketResponse.requestId();
            int ticketNumber = buyTicketResponse.ticketNumber();
            if(requestIdTicketNumberMapping.containsKey(requestId))
            {
                if(!requestIdTicketNumberMapping.get(requestId).equals(ticketNumber))
                {
                    System.out.println("INVARIANT FAILED: request ID received multiple ticket numbers");
                    invariant3 = false;
                }
            }else
            {
                requestIdTicketNumberMapping.put(requestId,ticketNumber);
            }
        }

        //INVARIANT 4 :
        if (statusResponse.soldTicketCount() != purchasedTicketNumbers.size())
        {
            System.out.println("INVARIANT FAILED: status sold count does not match actual sold tickets");
            invariant4 = false;
        }
        if(!statusResponseSoldTickets.equals(purchasedTicketNumbers))
        {
            System.out.println("INVARIANT FAILED : status sold tickets dont match actual sold tickets");
            invariant4 = false;
        }
        System.out.println("Purchased logical requests: " + purchasedCount);
        System.out.println("Tickets in status: " + statusResponse.soldTicketCount());
        System.out.println("Invariant 1 (no overselling)       :\t" + (invariant1 ? "PASS" : "FAIL"));
        System.out.println("Invariant 2 (unique ticket)        :\t" + (invariant2 ? "PASS" : "FAIL"));
        System.out.println("Invariant 3 (idempotent request)   :\t" + (invariant3 ? "PASS" : "FAIL"));
        System.out.println("Invariant 4 (status consistency)   :\t" + (invariant4 ? "PASS" : "FAIL"));
        System.out.println("overall                            :\t" + ((invariant1 && invariant2 && invariant3 && invariant4) ? "PASS" : "FAIL"));

        return invariant1 && invariant2 && invariant3 && invariant4;
    }

    private record TimedResponse(
            HttpResponse<String> response,
            long latency)
    {}

    private long percentile(List<Long> sortedLatencies, double percentile)
    {
        int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
        return sortedLatencies.get(index);
    }

}
