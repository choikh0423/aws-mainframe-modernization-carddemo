package com.carddemo.card.controller;

import com.carddemo.card.dto.CardListResponse;
import com.carddemo.card.dto.CardSelectionRequest;
import com.carddemo.card.dto.CardSelectionResponse;
import com.carddemo.card.service.CardListService;
import com.carddemo.card.service.CardSelectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CCLI — List Credit Cards (COCRDLIC). */
@RestController
@RequestMapping("/api/cards")
public class CardListController {

    private final CardListService listService;
    private final CardSelectionService selectionService;

    public CardListController(CardListService listService, CardSelectionService selectionService) {
        this.listService = listService;
        this.selectionService = selectionService;
    }

    /**
     * One page of the card list. {@code dir} is the AID: empty for ENTER,
     * {@code F} for PF8, {@code B} for PF7. The remaining parameters are the
     * page state from the previous response.
     */
    @GetMapping
    public CardListResponse list(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "cardNum", required = false) String cardNum,
            @RequestParam(name = "dir", required = false) String dir,
            @RequestParam(name = "pageNumber", required = false) Integer pageNumber,
            @RequestParam(name = "firstCardNum", required = false) String firstCardNum,
            @RequestParam(name = "lastCardNum", required = false) String lastCardNum,
            @RequestParam(name = "nextPageExists", required = false) Boolean nextPageExists,
            @RequestParam(name = "lastPageShown", required = false) Boolean lastPageShown) {
        return listService.list(acctId, cardNum, dir, pageNumber, firstCardNum, lastCardNum,
                nextPageExists, lastPageShown);
    }

    /**
     * The row selection flags. Returns the XCTL target for a valid S or U, or
     * 204 when ENTER was pressed with no row selected.
     */
    @PostMapping("/selection")
    public ResponseEntity<CardSelectionResponse> select(@RequestBody CardSelectionRequest request) {
        CardSelectionResponse response = selectionService.select(request);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }
}
