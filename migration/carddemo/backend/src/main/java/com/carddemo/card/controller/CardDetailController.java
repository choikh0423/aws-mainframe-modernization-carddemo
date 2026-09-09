package com.carddemo.card.controller;

import com.carddemo.card.dto.CardDetailResponse;
import com.carddemo.card.service.CardDetailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CCDL — View Credit Card detail (COCRDSLC). */
@RestController
@RequestMapping("/api/cards/detail")
public class CardDetailController {

    private final CardDetailService service;

    public CardDetailController(CardDetailService service) {
        this.service = service;
    }

    @GetMapping
    public CardDetailResponse detail(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "cardNum", required = false) String cardNum) {
        return service.detail(acctId, cardNum);
    }
}
