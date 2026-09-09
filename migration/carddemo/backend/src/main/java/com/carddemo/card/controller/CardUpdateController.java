package com.carddemo.card.controller;

import com.carddemo.card.dto.CardUpdateRequest;
import com.carddemo.card.dto.CardUpdateResponse;
import com.carddemo.card.service.CardUpdateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CCUP — Update Credit Card (COCRDUPC). */
@RestController
@RequestMapping("/api/cards/update")
public class CardUpdateController {

    private final CardUpdateService service;

    public CardUpdateController(CardUpdateService service) {
        this.service = service;
    }

    /** Fetch the card for editing (CCUP-DETAILS-NOT-FETCHED -> CCUP-SHOW-DETAILS). */
    @GetMapping
    public CardUpdateResponse load(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "cardNum", required = false) String cardNum) {
        return service.load(acctId, cardNum);
    }

    /** Submit the edited fields; {@code confirmed} is PF5. */
    @PostMapping
    public CardUpdateResponse update(@RequestBody CardUpdateRequest request) {
        return service.update(request);
    }
}
