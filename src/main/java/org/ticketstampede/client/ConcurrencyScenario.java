package org.ticketstampede.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.*;
import org.ticketstampede.service.payment.SimulatedPaymentService;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

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

        ConcurrencyScenario scenario = new ConcurrencyScenario(buyerClient,objectMapper);
        scenario.reservationConfirmationExpiryRace();
    }

    public void reservationConfirmationExpiryRace() throws JsonProcessingException, InterruptedException {
        ResetResponse resetResponse = objectMapper.readValue(buyerClient.reset(1).join().body(),ResetResponse.class);
        UUID requestId = UUID.randomUUID();
        String userId = "user-1";
        BuyTicketResponse buyTicketResponse = objectMapper.readValue(buyerClient.buy(requestId,userId).join().body(),BuyTicketResponse.class);

        SimulatedPayment simulatedPayment = objectMapper.readValue(buyerClient.payment(buyTicketResponse.reservationId(),userId).join().body(),SimulatedPayment.class);


        Instant expiresAt = buyTicketResponse.reservationExpiresAt();
        long waitMillis = Duration.between(
                Instant.now(),
                expiresAt
        ).toMillis()-10;

        if(waitMillis>0) Thread.sleep(waitMillis);

        long confirmationStart = System.nanoTime();
//        BuyTicketResponse confirmPaymentResponse = objectMapper.readValue(buyerClient.confirmReservation(simulatedPayment.paymentId()).join().body(),BuyTicketResponse.class);
        HttpResponse<String> confirmationResponse =
                buyerClient.confirmReservation(
                        simulatedPayment.paymentId()
                ).join();
        long confirmationEnd = System.nanoTime();

        System.out.println("confirmation time(seconds) : "+(confirmationEnd-confirmationStart)/1000000000.0);
        System.out.println("Initial buy ticket response : " +
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(buyTicketResponse)
        );
        System.out.println("simulated payment : " +
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(simulatedPayment)
        );
        System.out.println(
                "Confirmation HTTP status: "
                        + confirmationResponse.statusCode()
        );

        System.out.println(
                "Confirmation response: "
                        + confirmationResponse.body()
        );

        Thread.sleep(10000);
        HttpResponse<String> purchaseResponse =
                buyerClient.getPurchaseRequest(requestId).join();

        System.out.println(
                "Purchase request HTTP status: "
                        + purchaseResponse.statusCode()
        );

        System.out.println(
                "Final purchase response: "
                        + purchaseResponse.body()
        );
    }



}
