package org.ticketstampede.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.*;
import org.ticketstampede.entity.PurchaseStatus;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ConcurrencyScenario {

    private final BuyerClient buyerClient;
    private final ObjectMapper objectMapper;

    private ConcurrencyScenario(BuyerClient buyerClient,ObjectMapper objectMapper)
    {
        this.buyerClient = buyerClient;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        BuyerClient buyerClient = new BuyerClient(
                URI.create("http://localhost:8080"),
                objectMapper
        );

        ConcurrencyScenario demo = new ConcurrencyScenario(buyerClient, objectMapper);
        demo.concurrentBuys();
//        demo.buyAndReset();
//        demo.buyAndStatus();
//        demo.concurrentResets();
//        demo.buyAndResetAndStatus();
    }

    private void concurrentBuys() throws Exception
    {
        int capacity = 5;
        int buyerCount = 50;
        buyerClient.reset(capacity).join();
        List<CompletableFuture<HttpResponse<String>>> futurelist = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        List<BuyTicketResponse> buyTicketResponses = new ArrayList<>();
        for(int i=1;i<=buyerCount;i++)
        {
            UUID requestId = UUID.randomUUID();
            String userId = "user_"+i;
            futurelist.add(buyerClient.buy(requestId,userId));
            if(i%10==0)
            {
                futurelist.add(buyerClient.buy(requestId,userId));
            }
            if(i%12==0)
            {
                futurelist.add(buyerClient.buy(requestId,userId+3));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : futurelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                buyTicketResponses.add(objectMapper.readValue(response.body(),BuyTicketResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }
        StatusResponse statusResponse = objectMapper.readValue(buyerClient.status().join().body(),StatusResponse.class);

        int purchasedTicketsCount = buyTicketResponses
                .stream()
                .filter(buyTicketResponse -> buyTicketResponse.status()== PurchaseStatus.PURCHASED)
                .map(BuyTicketResponse::requestId)
                .collect(Collectors.toSet())
                .size();
        long soldOutResponses = buyTicketResponses
                .stream()
                .filter(buyTicketResponse -> buyTicketResponse.status()== PurchaseStatus.SOLD_OUT)
                .count();
        Set<Integer> purchasedTicketNumbers = buyTicketResponses
                .stream()
                .filter(response -> response.status() == PurchaseStatus.PURCHASED)
                .map(BuyTicketResponse::ticketNumber)
                .collect(Collectors.toSet());
        Set<Integer> statusTicketNumbers = statusResponse
                .ticketHolders()
                .stream()
                .map(TicketHolderResponse::ticketNumber)
                .collect(Collectors.toSet());
        Map<UUID, Integer> requestToTicket = new HashMap<>();
        boolean duplicateRequestsConsistent = true;
        for (BuyTicketResponse response : buyTicketResponses)
        {
            if (response.status() != PurchaseStatus.PURCHASED)
            {
                continue;
            }
            Integer previousTicket = requestToTicket.putIfAbsent(response.requestId(), response.ticketNumber()
            );
            if (previousTicket != null && !previousTicket.equals(response.ticketNumber()))
            {
                duplicateRequestsConsistent = false;
                System.out.println("Request ID mapped to multiple tickets :");
                System.out.println("Request ID          : " + response.requestId());
                System.out.println("Original ticket     : " + previousTicket);
                System.out.println("Conflicting ticket  : " + response.ticketNumber());
                break;
            }
        }
        long requestIdMismatchErrors = errorResponses
                .stream()
                .filter(error ->
                        error.code().equals("REQUEST_ID_USER_MISMATCH"))
                .count();

        boolean mismatchedUsersRejected =
                requestIdMismatchErrors == 4;

        boolean noOverselling =
                purchasedTicketsCount <= capacity;

        boolean uniqueTickets =
                purchasedTicketNumbers.size() == purchasedTicketsCount;

        boolean statusMatches =
                statusResponse.soldTicketCount() == purchasedTicketsCount
                        && statusTicketNumbers.equals(purchasedTicketNumbers);

        System.out.println("Capacity            : "+capacity);
        System.out.println("Buyer count         : "+buyerCount);
        System.out.println("Successful purchases: "+purchasedTicketsCount);
        System.out.println("Sold out count      : "+soldOutResponses);
        System.out.println("Errors              : "+errorResponses.size());

        System.out.println("Status sold count   : "+statusResponse.soldTicketCount());
        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponse)
        );
        System.out.println("No overselling       : " + (noOverselling ? "PASS" : "FAIL"));
        System.out.println("Unique tickets       : " + (uniqueTickets ? "PASS" : "FAIL"));
        System.out.println("Status matches       : " + (statusMatches ? "PASS" : "FAIL"));

        System.out.println("Errors : ");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResponses));
        System.out.println("Duplicate idempotency : " + (duplicateRequestsConsistent ? "PASS" : "FAIL"));
        System.out.println("Wrong user rejected   : " + (mismatchedUsersRejected ? "PASS" : "FAIL"));
    }

    private void buyAndReset() throws Exception
    {
        int capacity = 5;
        int buyerCount = 20;
        List<CompletableFuture<HttpResponse<String>>> buyTicketfuturelist = new ArrayList<>();
        List<CompletableFuture<HttpResponse<String>>> resetfuturelist = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        List<BuyTicketResponse> buyTicketResponses = new ArrayList<>();
        List<ResetResponse> resetResponses = new ArrayList<>();
//        errorResponses.add(objectMapper.readValue(buyerClient.buy(UUID.randomUUID(),"user-263").join().body(),ErrorResponse.class));
        resetfuturelist.add(buyerClient.reset(capacity));
        for(int i=1;i<=buyerCount;i++)
        {
            UUID requestId = UUID.randomUUID();
            String userId = "user_"+i;
            buyTicketfuturelist.add(buyerClient.buy(requestId,userId));
            if(i%5==0)
            {
                resetfuturelist.add(buyerClient.reset(capacity));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : buyTicketfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                buyTicketResponses.add(objectMapper.readValue(response.body(),BuyTicketResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : resetfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                resetResponses.add(objectMapper.readValue(response.body(),ResetResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }

        System.out.println("BUY RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(buyTicketResponses)
        );

        System.out.println("RESET RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(resetResponses)
        );

        System.out.println("ERROR RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(errorResponses)
        );
        HttpResponse<String> statusHttpResponse =
                buyerClient.status().join();
        StatusResponse statusResponse =
                objectMapper.readValue(
                        statusHttpResponse.body(),
                        StatusResponse.class
                );
        System.out.println("FINAL STATUS:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponse)
        );
    }

    private void buyAndStatus() throws Exception
    {
        int capacity = 5;
        int buyerCount = 20;
        List<CompletableFuture<HttpResponse<String>>> buyTicketfuturelist = new ArrayList<>();
        List<CompletableFuture<HttpResponse<String>>> statusfuturelist = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        List<BuyTicketResponse> buyTicketResponses = new ArrayList<>();
        List<StatusResponse> statusResponses = new ArrayList<>();
        buyerClient.reset(capacity).join();
        for(int i=1;i<=buyerCount;i++)
        {
            UUID requestId = UUID.randomUUID();
            String userId = "user_"+i;
            buyTicketfuturelist.add(buyerClient.buy(requestId,userId));
            if(i%5==0)
            {
                statusfuturelist.add(buyerClient.status());
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : buyTicketfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                buyTicketResponses.add(objectMapper.readValue(response.body(),BuyTicketResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : statusfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                statusResponses.add(objectMapper.readValue(response.body(),StatusResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }

        System.out.println("BUY RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(buyTicketResponses)
        );

        System.out.println("STATUS RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponses)
        );

        System.out.println("ERROR RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(errorResponses)
        );
        HttpResponse<String> statusHttpResponse =
                buyerClient.status().join();
        StatusResponse statusResponse =
                objectMapper.readValue(
                        statusHttpResponse.body(),
                        StatusResponse.class
                );
        System.out.println("FINAL STATUS:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponse)
        );
    }

    private void concurrentResets() throws Exception
    {
        int resetCount=10;
        int capacity = ThreadLocalRandom.current().nextInt(10, 51);
        List<CompletableFuture<HttpResponse<String>>> resetfuturelist = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        List<ResetResponse> resetResponses = new ArrayList<>();
        resetfuturelist.add(buyerClient.reset(capacity));
        for(int i=1;i<=resetCount;i++)
        {
            capacity = ThreadLocalRandom.current().nextInt(10, 51);
            resetfuturelist.add(buyerClient.reset(capacity));
        }
        for(CompletableFuture<HttpResponse<String>> future : resetfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                resetResponses.add(objectMapper.readValue(response.body(),ResetResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }

        System.out.println("RESET RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(resetResponses)
        );

        System.out.println("ERROR RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(errorResponses)
        );
        HttpResponse<String> statusHttpResponse =
                buyerClient.status().join();
        StatusResponse statusResponse =
                objectMapper.readValue(
                        statusHttpResponse.body(),
                        StatusResponse.class
                );
        System.out.println("FINAL STATUS:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponse)
        );
    }

    private void buyAndResetAndStatus() throws Exception
    {
        int capacity = 5;
        int buyerCount = 20;
        List<CompletableFuture<HttpResponse<String>>> buyTicketfuturelist = new ArrayList<>();
        List<CompletableFuture<HttpResponse<String>>> statusfuturelist = new ArrayList<>();
        List<CompletableFuture<HttpResponse<String>>> resetfuturelist = new ArrayList<>();
        List<ErrorResponse> errorResponses = new ArrayList<>();
        List<BuyTicketResponse> buyTicketResponses = new ArrayList<>();
        List<ResetResponse> resetResponses = new ArrayList<>();
        List<StatusResponse> statusResponses = new ArrayList<>();
        resetfuturelist.add(buyerClient.reset(capacity));
        for(int i=1;i<=buyerCount;i++)
        {
            UUID requestId = UUID.randomUUID();
            String userId = "user_"+i;
            buyTicketfuturelist.add(buyerClient.buy(requestId,userId));
            if(i%5==0)
            {
                resetfuturelist.add(buyerClient.reset(capacity));
            }
            if(i%4==0)
            {
                statusfuturelist.add(buyerClient.status());
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : buyTicketfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                buyTicketResponses.add(objectMapper.readValue(response.body(),BuyTicketResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : resetfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                resetResponses.add(objectMapper.readValue(response.body(),ResetResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }
        for(CompletableFuture<HttpResponse<String>> future : statusfuturelist)
        {
            HttpResponse<String> response = future.join();
            if(response.statusCode()==200)
            {
                statusResponses.add(objectMapper.readValue(response.body(),StatusResponse.class));
            }else
            {
                errorResponses.add(objectMapper.readValue(response.body(),ErrorResponse.class));
            }
        }

        System.out.println("BUY RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(buyTicketResponses)
        );

        System.out.println("RESET RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(resetResponses)
        );

        System.out.println("STATUS RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponses)
        );

        System.out.println("ERROR RESPONSES:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(errorResponses)
        );
        HttpResponse<String> statusHttpResponse =
                buyerClient.status().join();
        StatusResponse statusResponse =
                objectMapper.readValue(
                        statusHttpResponse.body(),
                        StatusResponse.class
                );
        System.out.println("FINAL STATUS:");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(statusResponse)
        );
    }
}
