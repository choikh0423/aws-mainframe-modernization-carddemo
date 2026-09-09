package com.carddemo.card.service;

import com.carddemo.card.dto.CardListRow;
import com.carddemo.card.dto.CardSelectionRequest;
import com.carddemo.card.dto.CardSelectionResponse;
import com.carddemo.card.exception.CardValidationException;
import com.carddemo.card.message.CardManagementMessages;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The CCLI selection-flag edit and the XCTL that follows
 * (COCRDLIC 2250-EDIT-ARRAY and 1000-MAIN's EVALUATE, COCRDLIC.cbl:1075-1121,
 * 517-569).
 *
 * <p>2250-EDIT-ARRAY walks the seven flags: a flag other than S/U/space/
 * LOW-VALUES is "INVALID ACTION CODE", and more than one S or U is
 * "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE". The count is taken over
 * the whole array before the routing decision, so the multi-select message wins
 * over a simultaneous invalid code on a later row.
 */
@Service
public class CardSelectionService {

    /** LIT-CARDDTLPGM / LIT-CARDDTLTRANID / mapset / map (COCRDLIC.cbl:78-84). */
    private static final String DETAIL_PROGRAM = "COCRDSLC";
    private static final String DETAIL_TRANID = "CCDL";
    private static final String DETAIL_MAPSET = "COCRDSL";
    private static final String DETAIL_MAP = "CCRDSLA";

    /** LIT-CARDUPDPGM / LIT-CARDUPDTRANID / mapset / map (COCRDLIC.cbl:85-91). */
    private static final String UPDATE_PROGRAM = "COCRDUPC";
    private static final String UPDATE_TRANID = "CCUP";
    private static final String UPDATE_MAPSET = "COCRDUP";
    private static final String UPDATE_MAP = "CCRDUPA";

    public CardSelectionResponse select(CardSelectionRequest request) {
        List<String> flags = request.getFlags() == null ? List.of() : request.getFlags();
        List<CardListRow> rows = request.getRows() == null ? List.of() : request.getRows();

        int selectedCount = 0;
        int selectedIndex = -1;
        String selectedFlag = null;
        boolean invalidCode = false;

        for (int i = 0; i < flags.size(); i++) {
            String flag = flags.get(i) == null ? "" : flags.get(i).trim().toUpperCase();
            if (flag.isEmpty()) {
                continue;
            }
            if ("S".equals(flag) || "U".equals(flag)) {
                selectedCount++;
                selectedIndex = i;
                selectedFlag = flag;
            } else {
                invalidCode = true;
            }
        }

        if (selectedCount > 1) {
            throw new CardValidationException(CardManagementMessages.MORE_THAN_ONE_ACTION);
        }
        if (invalidCode) {
            throw new CardValidationException(CardManagementMessages.INVALID_ACTION_CODE);
        }
        if (selectedCount == 0 || selectedIndex >= rows.size()) {
            // ENTER with nothing selected: the map is simply re-sent (WHEN OTHER).
            return null;
        }

        CardListRow row = rows.get(selectedIndex);
        if ("S".equals(selectedFlag)) {
            return new CardSelectionResponse(DETAIL_PROGRAM, DETAIL_TRANID, DETAIL_MAPSET, DETAIL_MAP,
                    row.getAcctId(), row.getCardNum());
        }
        return new CardSelectionResponse(UPDATE_PROGRAM, UPDATE_TRANID, UPDATE_MAPSET, UPDATE_MAP,
                row.getAcctId(), row.getCardNum());
    }
}
