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

@RestController
public class TicketController {
    private final PurchaseService purchaseService;
    private final SaleVersionService saleVersionService;
    private final StatusService statusService;

    public TicketController(PurchaseService purchaseService,
                            SaleVersionService saleVersionService,
                            StatusService statusService)
    {
        this.purchaseService = purchaseService;
        this.saleVersionService = saleVersionService;
        this.statusService = statusService;
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

    @GetMapping("/status")
    public StatusResponse status()
    {
        return statusService.getStatus();
    }

}
