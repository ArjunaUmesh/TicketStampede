package org.ticketstampede.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.*;
import org.ticketstampede.entity.PurchaseStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BuyerClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String RESET_ENDPOINT = "/reset";
    private static final String BUY_ENDPOINT = "/buy";
    private static final String STATUS_ENDPOINT = "/status";

    public BuyerClient(URI baseUri, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public CompletableFuture<HttpResponse<String>> reset(int capacity) throws JsonProcessingException {
        //CompletableFuture<T> represents the result of an asynchronous computation that may complete in the future.
        //CompletableFuture.join() waits for an asynchronous computation to complete and returns its result.
        ResetRequest resetRequest = new ResetRequest(capacity);
        String requestBody = objectMapper.writeValueAsString(resetRequest);

        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(baseUri.resolve(RESET_ENDPOINT))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> buy(UUID requestId, String userId) throws JsonProcessingException {
        BuyTicketRequest buyTicketRequest = new BuyTicketRequest(userId,requestId);
        String requestBody = objectMapper.writeValueAsString(buyTicketRequest);

        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(baseUri.resolve(BUY_ENDPOINT))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> status() {
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(baseUri.resolve(STATUS_ENDPOINT))
                .GET()
                .build();
        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString());
    }
}
