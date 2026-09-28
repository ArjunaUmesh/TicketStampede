package org.ticketstampede.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.ticketstampede.dto.*;
import org.ticketstampede.service.PurchaseService;
import org.ticketstampede.service.SaleVersionService;
import org.ticketstampede.service.StatusService;
import org.ticketstampede.service.payment.PaymentService;

import java.util.UUID;

@RestController
public class TicketController {
    private final PurchaseService purchaseService;
    private final SaleVersionService saleVersionService;
    private final StatusService statusService;
    private final PaymentService paymentService;

    public TicketController(PurchaseService purchaseService,
                            SaleVersionService saleVersionService,
                            StatusService statusService,
                            PaymentService paymentService)
    {
        this.purchaseService = purchaseService;
        this.saleVersionService = saleVersionService;
        this.statusService = statusService;
        this.paymentService = paymentService;
    }

    @PostMapping("/reset")
    public ResetResponse reset(@Valid @RequestBody ResetRequest resetRequest)
    {
        return saleVersionService.createSaleVersion(resetRequest.capacity());
    }

    @PostMapping("/buy")
    public BuyTicketResponse buy(@Valid @RequestBody BuyTicketRequest buyTicketRequest)
    {
        return purchaseService.buyTicket(buyTicketRequest.userId(),buyTicketRequest.requestId());
    }

    @PostMapping("/payment")
    public SimulatedPayment payment(@Valid @RequestBody PaymentRequest paymentRequest)
    {
        return paymentService.authorize(paymentRequest.reservationId(),paymentRequest.userId());
    }

    @PostMapping("/confirmReservation")
    public BuyTicketResponse confirmPurchase(@Valid @RequestBody ConfirmPurchaseRequest confirmPurchaseRequest)
    {
        return purchaseService.confirmPurchase(confirmPurchaseRequest.paymentId());
    }

    @GetMapping("/status")
    public StatusResponse status()
    {
        return statusService.getStatus();
    }

}
