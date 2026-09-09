package com.carddemo.trantype.service;

import com.carddemo.common.domain.Db2TransactionTypeRecord;
import com.carddemo.common.repository.Db2TransactionTypeRepository;
import com.carddemo.trantype.dto.TranTypeListDisplayRow;
import com.carddemo.trantype.dto.TranTypeListRequest;
import com.carddemo.trantype.dto.TranTypeListResponse;
import com.carddemo.trantype.dto.TranTypeListRow;
import com.carddemo.trantype.dto.TranTypeListRowInput;
import com.carddemo.trantype.dto.TranTypeListState;
import com.carddemo.trantype.message.TranTypeMessages;
import com.carddemo.trantype.repository.TranTypeBrowseRepository;
import com.carddemo.trantype.repository.TranTypeCategoryLookupRepository;
import com.carddemo.trantype.validator.TranTypeValidator;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CTLI — {@code COTRTLIC.cbl}. One call of {@link #handle} is one CICS task:
 * receive the map, edit it, act on the AID key, and send the map back.
 *
 * <p>The paragraph structure of the COBOL is kept: {@code 1200-EDIT-INPUTS}
 * runs the array edit before the filter edits, the AID key is validated and
 * downgraded to ENTER exactly as {@code 0000-MAIN} does, the browse cursors are
 * driven by {@code 8000-READ-FORWARD} / {@code 8100-READ-BACKWARDS}, and the
 * message line is chosen by the {@code 2500-SETUP-MESSAGE} ladder.
 */
@Service
public class TranTypeListService {

    /** {@code WS-MAX-SCREEN-LINES}. */
    public static final int MAX_SCREEN_LINES = 7;

    private static final String THIS_PGM = "COTRTLIC";
    private static final String THIS_TRANID = "CTLI";
    private static final String ADMIN_PGM = "COADM01C";
    private static final String ADMIN_TRANID = "CA00";
    private static final String UPDATE_PGM = "COTRTUPC";
    private static final String UPDATE_TRANID = "CTTU";

    private static final String AID_ENTER = "ENTER";
    private static final String AID_PF2 = "PF2";
    private static final String AID_PF3 = "PF3";
    private static final String AID_PF7 = "PF7";
    private static final String AID_PF8 = "PF8";
    private static final String AID_PF10 = "PF10";

    private static final String DELETE_FLAG = "D";
    private static final String UPDATE_FLAG = "U";

    private final TranTypeBrowseRepository browseRepository;
    private final Db2TransactionTypeRepository transactionTypeRepository;
    private final TranTypeCategoryLookupRepository categoryLookupRepository;
    private final TranTypeValidator validator;

    public TranTypeListService(TranTypeBrowseRepository browseRepository,
                               Db2TransactionTypeRepository transactionTypeRepository,
                               TranTypeCategoryLookupRepository categoryLookupRepository,
                               TranTypeValidator validator) {
        this.browseRepository = browseRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.categoryLookupRepository = categoryLookupRepository;
        this.validator = validator;
    }

    @Transactional
    public TranTypeListResponse handle(TranTypeListRequest request) {
        Turn turn = new Turn(request);

        if (turn.firstEntry) {
            turn.startKey = "";
            readForward(turn);
            return sendMap(turn);
        }

        editInputs(turn);
        String aid = resolveAid(turn);

        if (AID_PF3.equals(aid)) {
            return transfer(ADMIN_PGM, ADMIN_TRANID);
        }
        if (AID_PF2.equals(aid)) {
            return transfer(UPDATE_PGM, UPDATE_TRANID);
        }
        if (!AID_PF8.equals(aid)) {
            turn.state.setLastPageShown(false);
        }
        turn.aid = aid;

        if (turn.inputError) {
            turn.startKey = turn.state.getFirstTypeCode();
            if (!turn.typeFilterNotOk && !turn.descFilterNotOk) {
                readForward(turn);
            }
        } else if (AID_PF7.equals(aid) && turn.state.isFirstPage()) {
            turn.startKey = turn.state.getFirstTypeCode();
            readForward(turn);
        } else if (AID_PF8.equals(aid) && turn.state.isNextPageExists()) {
            turn.startKey = turn.state.getLastTypeCode();
            turn.state.setScreenNum(turn.state.getScreenNum() + 1);
            readForward(turn);
            turn.clearSelections();
        } else if (AID_PF7.equals(aid)) {
            turn.startKey = turn.state.getFirstTypeCode();
            turn.state.setScreenNum(turn.state.getScreenNum() - 1);
            readBackward(turn);
            turn.clearSelections();
        } else if (AID_PF10.equals(aid) && turn.deletesRequested > 0) {
            deleteRecord(turn);
        } else if (AID_PF10.equals(aid) && turn.updatesRequested > 0) {
            updateRecord(turn);
            turn.startKey = turn.state.getFirstTypeCode();
            readForward(turn);
        } else {
            turn.startKey = turn.state.getFirstTypeCode();
            readForward(turn);
        }

        TranTypeListResponse response = sendMap(turn);

        if (turn.deleted) {
            // COTRTLIC.cbl:826-833 - a successful delete restarts the conversation.
            TranTypeListState fresh = TranTypeListState.firstEntry();
            return new TranTypeListResponse(response.typeFilter(), response.descFilter(), response.rows(),
                    response.pageNumber(), response.infoMessage(), response.errorMessage(),
                    response.protectSelectRows(), response.nextProgram(), response.nextTranId(), fresh);
        }
        return response;
    }

    // ------------------------------------------------------------ 1200-EDIT-INPUTS

    /**
     * {@code 1200-EDIT-INPUTS} (COTRTLIC.cbl:960-976). The order matters: the
     * array is edited first, then the description filter, then the type filter,
     * then the cross edit. Because the "filter changed" flags are only set by
     * the two filter edits, the array edit at COTRTLIC.cbl:1091-1094 always sees
     * them unset — so a row flagged in the same keystroke as a filter change is
     * still processed (FR-L22).
     */
    private void editInputs(Turn turn) {
        editArray(turn);
        editDescriptionFilter(turn);
        editTypeFilter(turn);
        crossEdits(turn);
    }

    /** {@code 1210-EDIT-ARRAY} (COTRTLIC.cbl:982-1053). */
    private void editArray(Turn turn) {
        int selectedIndex = 0;
        for (int i = 0; i < MAX_SCREEN_LINES; i++) {
            String flag = turn.inputRows.get(i).flag();
            if (DELETE_FLAG.equals(flag)) {
                turn.deletesRequested++;
            } else if (UPDATE_FLAG.equals(flag)) {
                turn.updatesRequested++;
            }
        }
        turn.actionsRequested = turn.deletesRequested + turn.updatesRequested + turn.invalidFlagCount();
        turn.validActionsSelected = turn.deletesRequested + turn.updatesRequested;

        // The COBOL walks the rows from 7 down to 1, so the lowest flagged row wins.
        for (int i = MAX_SCREEN_LINES; i >= 1; i--) {
            TranTypeListRowInput input = turn.inputRows.get(i - 1);
            String flag = input.flag();
            if (DELETE_FLAG.equals(flag) || UPDATE_FLAG.equals(flag)) {
                selectedIndex = i;
                if (turn.moreThanOneAction()) {
                    turn.rowInError[i - 1] = true;
                }
                if (UPDATE_FLAG.equals(flag)) {
                    editArrayDescription(turn, i);
                }
            } else if (!flag.isEmpty()) {
                turn.inputError = true;
                turn.rowInError[i - 1] = true;
                turn.returnMessage = TranTypeMessages.LIST_INVALID_ACTION_CODE;
            }
        }

        turn.rowSelectionChanged = selectedIndex != turn.state.getRowSelected();
        turn.state.setRowSelected(selectedIndex);
        turn.selectedIndex = selectedIndex;

        if (turn.moreThanOneAction()) {
            turn.inputError = true;
            turn.returnMessage = TranTypeMessages.LIST_MORE_THAN_1_ACTION;
        }
    }

    /** {@code 1211-EDIT-ARRAY-DESC} (COTRTLIC.cbl:1060-1089). */
    private void editArrayDescription(Turn turn, int rowNumber) {
        String typed = safe(turn.inputRows.get(rowNumber - 1).description());
        String fetched = rowNumber <= turn.state.getRows().size()
                ? safe(turn.state.getRows().get(rowNumber - 1).description())
                : "";

        if (typed.trim().equalsIgnoreCase(fetched.trim())
                && typed.trim().length() == fetched.trim().length()) {
            turn.infoMessage = TranTypeMessages.LIST_NO_CHANGES_DETECTED;
            turn.changesFound = false;
            return;
        }
        turn.changesFound = true;

        String error = validator.validateDescription(typed);
        if (error != null) {
            turn.inputError = true;
            turn.rowDescriptionBlank = typed.trim().isEmpty();
            if (turn.returnMessage == null) {
                turn.returnMessage = error;
            }
        }
    }

    /** {@code 1220-EDIT-TYPECD} and its exit (COTRTLIC.cbl:1096-1140). */
    private void editTypeFilter(Turn turn) {
        String input = safe(turn.typeFilterIn).trim();
        boolean blank = input.isEmpty() || isAllZeros(input);

        if (blank) {
            turn.typeFilterBlank = true;
        } else if (!validator.isNumeric(input)) {
            turn.inputError = true;
            turn.typeFilterNotOk = true;
            turn.protectSelectRows = true;
            turn.returnMessage = TranTypeMessages.LIST_TYPE_FILTER_INVALID;
        } else {
            turn.typeFilterValid = true;
            turn.typeCodeFilter = input;
        }

        String previous = safe(turn.state.getTypeFilter()).trim();
        if (input.equals(previous) || (blank && (previous.isEmpty() || isAllZeros(previous)))) {
            turn.typeFilterChanged = false;
        } else {
            turn.typeFilterChanged = true;
            turn.state.resetPaging();
            turn.state.setTypeFilter(input);
        }
    }

    /** {@code 1230-EDIT-DESC} and its exit (COTRTLIC.cbl:1142-1178). */
    private void editDescriptionFilter(Turn turn) {
        String input = safe(turn.descFilterIn);
        boolean blank = input.trim().isEmpty();

        if (!blank) {
            turn.descFilterValid = true;
            turn.descriptionFilter = "%" + input.trim() + "%";
        }

        String previous = safe(turn.state.getDescFilter());
        if (input.trim().equals(previous.trim()) || (blank && previous.trim().isEmpty())) {
            turn.descFilterChanged = false;
        } else {
            turn.descFilterChanged = true;
            turn.state.resetPaging();
            turn.state.setDescFilter(input);
        }
    }

    /** {@code 1290-CROSS-EDITS} (COTRTLIC.cbl:1239-1268). */
    private void crossEdits(Turn turn) {
        if (!turn.typeFilterValid && !turn.descFilterValid) {
            return;
        }
        long count = browseRepository.countMatching(turn.typeCodeFilter, turn.descriptionFilter);
        if (count == 0) {
            turn.inputError = true;
            if (turn.typeFilterValid) {
                turn.typeFilterNotOk = true;
            }
            if (turn.descFilterValid) {
                turn.descFilterNotOk = true;
            }
            turn.protectSelectRows = true;
            turn.returnMessage = TranTypeMessages.LIST_NO_RECORDS_FOR_FILTERS;
        }
    }

    // ------------------------------------------------------------ AID handling

    /**
     * COTRTLIC.cbl:565-580 and 651-663: only ENTER, F2, F3, F7, F8 and — while
     * an action is pending — F10 are valid; every other key is treated as
     * ENTER. F10 is downgraded to ENTER when the operator also changed a filter
     * or moved the selection, so the confirmation always applies to the screen
     * the operator confirmed.
     */
    private String resolveAid(Turn turn) {
        String aid = safe(turn.requestedAid).trim().toUpperCase();
        boolean pending = turn.state.isDeleteRequested() || turn.state.isUpdateRequested();

        boolean valid = AID_ENTER.equals(aid) || AID_PF2.equals(aid) || AID_PF3.equals(aid)
                || AID_PF7.equals(aid) || AID_PF8.equals(aid)
                || (AID_PF10.equals(aid) && pending);
        if (!valid) {
            return AID_ENTER;
        }
        if (AID_PF10.equals(aid)
                && (turn.typeFilterChanged || turn.descFilterChanged || turn.rowSelectionChanged)) {
            return AID_ENTER;
        }
        return aid;
    }

    // ------------------------------------------------------------ browse

    /** {@code 8000-READ-FORWARD} (COTRTLIC.cbl:1603-1723). */
    private void readForward(Turn turn) {
        String startKey = safe(turn.startKey);
        List<Db2TransactionTypeRecord> fetched = browseRepository.readForward(
                startKey, turn.typeCodeFilter, turn.descriptionFilter,
                PageRequest.of(0, MAX_SCREEN_LINES + 1));

        List<TranTypeListRow> rows = new ArrayList<>();
        int rowNumber = 0;
        for (Db2TransactionTypeRecord record : fetched) {
            if (rowNumber == MAX_SCREEN_LINES) {
                break;
            }
            rowNumber++;
            rows.add(new TranTypeListRow(record.getTrType(), rtrim(record.getTrDescription())));
            if (rowNumber == 1) {
                turn.state.setFirstTypeCode(record.getTrType());
                if (turn.state.getScreenNum() == 0) {
                    turn.state.setScreenNum(1);
                }
            }
            turn.state.setLastTypeCode(record.getTrType());
        }

        boolean nextPageExists = fetched.size() > MAX_SCREEN_LINES;
        turn.state.setNextPageExists(nextPageExists);
        if (nextPageExists) {
            // The COBOL reads one row past the page and remembers it as the
            // restart key of the next page (COTRTLIC.cbl:1666-1673).
            turn.state.setLastTypeCode(fetched.get(MAX_SCREEN_LINES).getTrType());
        } else if (turn.returnMessage == null && AID_PF8.equals(turn.aid)) {
            turn.returnMessage = TranTypeMessages.LIST_NO_MORE_RECORDS;
        }

        if (rows.isEmpty() && turn.state.getScreenNum() <= 1) {
            turn.noRecordsFound = true;
            turn.returnMessage = TranTypeMessages.LIST_NO_RECORDS_FOUND;
        }

        padRows(rows);
        turn.state.setRows(rows);
    }

    /** {@code 8100-READ-BACKWARDS} (COTRTLIC.cbl:1727-1799). */
    private void readBackward(Turn turn) {
        List<Db2TransactionTypeRecord> fetched = browseRepository.readBackward(
                safe(turn.state.getFirstTypeCode()), turn.typeCodeFilter, turn.descriptionFilter,
                PageRequest.of(0, MAX_SCREEN_LINES));

        List<TranTypeListRow> rows = new ArrayList<>();
        for (int i = fetched.size() - 1; i >= 0; i--) {
            Db2TransactionTypeRecord record = fetched.get(i);
            rows.add(new TranTypeListRow(record.getTrType(), rtrim(record.getTrDescription())));
        }
        if (!rows.isEmpty()) {
            turn.state.setFirstTypeCode(rows.get(0).typeCode());
            turn.state.setLastTypeCode(rows.get(rows.size() - 1).typeCode());
        }
        turn.state.setNextPageExists(true);
        padRows(rows);
        turn.state.setRows(rows);
    }

    // ------------------------------------------------------------ update / delete

    /** {@code 9200-UPDATE-RECORD} (COTRTLIC.cbl:1837-1894). */
    private void updateRecord(Turn turn) {
        int index = turn.selectedIndex;
        String typeCode = turn.state.getRows().get(index - 1).typeCode();
        String description = safe(turn.inputRows.get(index - 1).description()).trim();

        Optional<Db2TransactionTypeRecord> existing = transactionTypeRepository.findById(typeCode);
        if (existing.isEmpty()) {
            turn.state.setUpdateRequested(true);
            turn.returnMessage = TranTypeMessages.withSqlCode(TranTypeMessages.LIST_UPDATE_NOT_FOUND, 100);
            return;
        }
        Db2TransactionTypeRecord record = existing.get();
        record.setTrDescription(description);
        try {
            transactionTypeRepository.save(record);
        } catch (PessimisticLockingFailureException lockFailure) {
            turn.state.setUpdateRequested(true);
            turn.inputError = true;
            turn.returnMessage =
                    TranTypeMessages.withSqlCode(TranTypeMessages.LIST_UPDATE_DEADLOCK, -911);
            return;
        }

        turn.updated = true;
        turn.state.setUpdateRequested(false);
    }

    /** {@code 9300-DELETE-RECORD} (COTRTLIC.cbl:1896-1944). */
    private void deleteRecord(Turn turn) {
        int index = turn.selectedIndex;
        String typeCode = turn.state.getRows().get(index - 1).typeCode();

        if (categoryLookupRepository.existsForTypeCode(typeCode)) {
            turn.state.setDeleteRequested(true);
            turn.returnMessage =
                    TranTypeMessages.withSqlCode(TranTypeMessages.LIST_DELETE_HAS_CHILDREN, -532);
            return;
        }
        if (!transactionTypeRepository.existsById(typeCode)) {
            turn.returnMessage = TranTypeMessages.withSqlCode(TranTypeMessages.LIST_DELETE_FAILED, 100);
            return;
        }
        transactionTypeRepository.deleteById(typeCode);
        turn.deleted = true;
        turn.state.setDeleteRequested(false);
    }

    // ------------------------------------------------------------ 2000-SEND-MAP

    private TranTypeListResponse sendMap(Turn turn) {
        List<TranTypeListDisplayRow> displayRows = buildDisplayRows(turn);
        String infoMessage = setupMessage(turn);

        return new TranTypeListResponse(
                safe(turn.typeFilterIn),
                safe(turn.descFilterIn),
                displayRows,
                turn.state.getScreenNum(),
                infoMessage,
                turn.returnMessage == null ? "" : turn.returnMessage,
                turn.protectSelectRows,
                THIS_PGM,
                THIS_TRANID,
                turn.state);
    }

    /** {@code 2300-SCREEN-ARRAY-INIT} (COTRTLIC.cbl:1383-1431). */
    private List<TranTypeListDisplayRow> buildDisplayRows(Turn turn) {
        List<TranTypeListDisplayRow> displayRows = new ArrayList<>();
        turn.state.setDeleteRequested(false);
        turn.state.setUpdateRequested(false);

        for (int i = 1; i <= MAX_SCREEN_LINES; i++) {
            TranTypeListRow row = turn.state.getRows().get(i - 1);
            if (row.isEmpty()) {
                displayRows.add(new TranTypeListDisplayRow("", "", "", false, false));
                continue;
            }

            String flag = turn.inputRows.get(i - 1).flag();
            boolean onlyOneValidAction = turn.validActionsSelected == 1;
            boolean noBadActions = !turn.hasBadActions();
            String description = row.description();
            boolean highlighted = false;
            String selection = flag;

            if (DELETE_FLAG.equals(flag) && onlyOneValidAction && noBadActions) {
                if (turn.deleted) {
                    selection = "";
                } else {
                    turn.state.setDeleteRequested(true);
                    highlighted = true;
                }
            }

            if (UPDATE_FLAG.equals(flag) && onlyOneValidAction && noBadActions) {
                if (turn.updated) {
                    selection = "";
                } else {
                    turn.state.setUpdateRequested(true);
                    highlighted = true;
                }
                if (turn.changesFound) {
                    description = turn.rowDescriptionBlank
                            ? "*"
                            : safe(turn.inputRows.get(i - 1).description());
                }
            }

            displayRows.add(new TranTypeListDisplayRow(row.typeCode(), description, selection,
                    highlighted, turn.rowInError[i - 1]));
        }
        return displayRows;
    }

    /** {@code 2500-SETUP-MESSAGE} (COTRTLIC.cbl:1504-1555). */
    private String setupMessage(Turn turn) {
        if (turn.deleted) {
            return TranTypeMessages.LIST_INFO_DELETE_SUCCESS;
        }
        if (turn.updated) {
            return TranTypeMessages.LIST_INFO_UPDATE_SUCCESS;
        }
        if (turn.typeFilterNotOk || turn.descFilterNotOk) {
            return blankIfNoRecords(turn, turn.infoMessage);
        }
        boolean onlyOneAction = turn.actionsRequested == 1 && turn.validActionsSelected == 1;
        if (AID_ENTER.equals(turn.aid) && turn.deletesRequested > 0 && onlyOneAction) {
            if (turn.infoMessage == null && !turn.typeFilterChanged && !turn.descFilterChanged) {
                return TranTypeMessages.LIST_INFO_DELETE;
            }
            return blankIfNoRecords(turn, turn.infoMessage);
        }
        if (AID_ENTER.equals(turn.aid) && turn.updatesRequested > 0 && onlyOneAction) {
            if (turn.infoMessage == null && !turn.typeFilterChanged && !turn.descFilterChanged) {
                return TranTypeMessages.LIST_INFO_UPDATE;
            }
            return blankIfNoRecords(turn, turn.infoMessage);
        }
        if (AID_PF7.equals(turn.aid) && turn.state.isFirstPage()) {
            turn.returnMessage = TranTypeMessages.LIST_NO_PREVIOUS_PAGES;
            return blankIfNoRecords(turn, turn.infoMessage);
        }
        if (AID_PF8.equals(turn.aid) && !turn.state.isNextPageExists() && turn.state.isLastPageShown()) {
            turn.returnMessage = TranTypeMessages.LIST_NO_MORE_PAGES;
            return blankIfNoRecords(turn, turn.infoMessage);
        }
        if (AID_PF8.equals(turn.aid) && !turn.state.isNextPageExists()) {
            turn.state.setLastPageShown(true);
            String info = turn.infoMessage == null ? TranTypeMessages.LIST_INFO_REC_ACTIONS : turn.infoMessage;
            return blankIfNoRecords(turn, info);
        }
        if (turn.infoMessage == null || turn.state.isNextPageExists()) {
            return blankIfNoRecords(turn, TranTypeMessages.LIST_INFO_REC_ACTIONS);
        }
        return blankIfNoRecords(turn, turn.infoMessage);
    }

    /**
     * COTRTLIC.cbl:1575-1579: the info line stays empty while the browse found
     * nothing, so the "no records" text on the error line is the only message.
     */
    private String blankIfNoRecords(Turn turn, String info) {
        if (info == null || turn.noRecordsFound) {
            return "";
        }
        return info;
    }

    private TranTypeListResponse transfer(String program, String tranId) {
        return new TranTypeListResponse("", "", padDisplayRows(new ArrayList<>()), 0, "", "",
                false, program, tranId, TranTypeListState.firstEntry());
    }

    // ------------------------------------------------------------ helpers

    private static void padRows(List<TranTypeListRow> rows) {
        while (rows.size() < MAX_SCREEN_LINES) {
            rows.add(TranTypeListRow.empty());
        }
    }

    private static List<TranTypeListDisplayRow> padDisplayRows(List<TranTypeListDisplayRow> rows) {
        while (rows.size() < MAX_SCREEN_LINES) {
            rows.add(new TranTypeListDisplayRow("", "", "", false, false));
        }
        return rows;
    }

    private static boolean isAllZeros(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return value.chars().allMatch(c -> c == '0');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String rtrim(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    /** The working storage of one CTLI task ({@code WS-MISC-STORAGE}). */
    private static final class Turn {

        private final TranTypeListState state;
        private final List<TranTypeListRowInput> inputRows;
        private final boolean[] rowInError = new boolean[MAX_SCREEN_LINES];
        private final boolean firstEntry;
        private final String requestedAid;
        private final String typeFilterIn;
        private final String descFilterIn;

        private String aid = AID_ENTER;
        private String startKey = "";
        private String typeCodeFilter;
        private String descriptionFilter;
        private String returnMessage;
        private String infoMessage;

        private boolean inputError;
        private boolean typeFilterValid;
        private boolean typeFilterNotOk;
        private boolean typeFilterBlank;
        private boolean typeFilterChanged;
        private boolean descFilterValid;
        private boolean descFilterNotOk;
        private boolean descFilterChanged;
        private boolean rowSelectionChanged;
        private boolean protectSelectRows;
        private boolean changesFound;
        private boolean rowDescriptionBlank;
        private boolean noRecordsFound;
        private boolean updated;
        private boolean deleted;

        private int actionsRequested;
        private int validActionsSelected;
        private int deletesRequested;
        private int updatesRequested;
        private int selectedIndex;

        private Turn(TranTypeListRequest request) {
            this.firstEntry = request.state() == null;
            this.state = firstEntry ? TranTypeListState.firstEntry() : request.state().copy();
            this.requestedAid = request.aid();
            this.typeFilterIn = request.typeFilter();
            this.descFilterIn = request.descFilter();
            this.inputRows = new ArrayList<>();
            List<TranTypeListRowInput> supplied = request.rows() == null ? List.of() : request.rows();
            for (int i = 0; i < MAX_SCREEN_LINES; i++) {
                inputRows.add(i < supplied.size() && supplied.get(i) != null
                        ? supplied.get(i)
                        : TranTypeListRowInput.blank());
            }
            padRows(this.state.getRows());
        }

        private int invalidFlagCount() {
            int invalid = 0;
            for (TranTypeListRowInput input : inputRows) {
                String flag = input.flag();
                if (!flag.isEmpty() && !DELETE_FLAG.equals(flag) && !UPDATE_FLAG.equals(flag)) {
                    invalid++;
                }
            }
            return invalid;
        }

        private boolean moreThanOneAction() {
            return actionsRequested > 1;
        }

        private boolean hasBadActions() {
            for (boolean inError : rowInError) {
                if (inError) {
                    return true;
                }
            }
            return false;
        }

        /** {@code INITIALIZE WS-EDIT-SELECT-FLAGS} after a page turn. */
        private void clearSelections() {
            inputRows.replaceAll(input -> TranTypeListRowInput.blank());
            deletesRequested = 0;
            updatesRequested = 0;
            actionsRequested = 0;
            validActionsSelected = 0;
            selectedIndex = 0;
            state.setRowSelected(0);
        }
    }
}
