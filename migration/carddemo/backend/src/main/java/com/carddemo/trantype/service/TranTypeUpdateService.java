package com.carddemo.trantype.service;

import com.carddemo.common.domain.Db2TransactionTypeRecord;
import com.carddemo.common.repository.Db2TransactionTypeRepository;
import com.carddemo.trantype.dto.TranTypeUpdateRequest;
import com.carddemo.trantype.dto.TranTypeUpdateResponse;
import com.carddemo.trantype.dto.TranTypeUpdateState;
import com.carddemo.trantype.exception.TranTypeAbendException;
import com.carddemo.trantype.message.TranTypeMessages;
import com.carddemo.trantype.repository.TranTypeCategoryLookupRepository;
import com.carddemo.trantype.validator.TranTypeValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CTTU — {@code COTRTUPC.cbl}. One call of {@link #handle} is one CICS task.
 *
 * <p>The screen is a state machine held in {@code TTUP-CHANGE-ACTION}; the
 * paragraph structure of the COBOL is preserved: {@code 0001-CHECK-PFKEYS}
 * validates the AID against the current state, the "simulate initial entry"
 * block of {@code 0000-MAIN} (COTRTUPC.cbl:405-419) resets finished
 * conversations, {@code 1000-PROCESS-INPUTS} edits the map,
 * {@code 2000-DECIDE-ACTION} picks the next state and {@code 3000-SEND-MAP}
 * decides which values, messages and attributes go back to the terminal.
 */
@Service
public class TranTypeUpdateService {

    private static final String THIS_PGM = "COTRTUPC";
    private static final String THIS_TRANID = "CTTU";
    private static final String ADMIN_PGM = "COADM01C";
    private static final String ADMIN_TRANID = "CA00";

    private static final String AID_ENTER = "ENTER";
    private static final String AID_PF3 = "PF3";
    private static final String AID_PF4 = "PF4";
    private static final String AID_PF5 = "PF5";
    private static final String AID_PF12 = "PF12";

    private final Db2TransactionTypeRepository transactionTypeRepository;
    private final TranTypeCategoryLookupRepository categoryLookupRepository;
    private final TranTypeValidator validator;

    public TranTypeUpdateService(Db2TransactionTypeRepository transactionTypeRepository,
                                 TranTypeCategoryLookupRepository categoryLookupRepository,
                                 TranTypeValidator validator) {
        this.transactionTypeRepository = transactionTypeRepository;
        this.categoryLookupRepository = categoryLookupRepository;
        this.validator = validator;
    }

    @Transactional
    public TranTypeUpdateResponse handle(TranTypeUpdateRequest request) {
        Turn turn = new Turn(request);

        checkPfKeys(turn);
        simulateInitialEntry(turn);

        if (AID_PF3.equals(turn.aid)) {
            // COTRTUPC.cbl:429-460 - XCTL back to the caller, admin menu by default.
            return transfer();
        }

        if (!turn.state.isProgramReenter() || turn.programEnter) {
            // COTRTUPC.cbl:465-478 - clear the screen and ask for the key.
            turn.state = TranTypeUpdateState.firstEntry();
            turn.returnMessage = null;
            turn.programEnter = true;
            TranTypeUpdateResponse response = sendMap(turn);
            turn.state.setProgramReenter(true);
            return response;
        }

        if (AID_PF4.equals(turn.aid) && turn.state.isState(TranTypeUpdateState.CONFIRM_DELETE)) {
            turn.state.setChangeAction(TranTypeUpdateState.START_DELETE);
            deleteProcessing(turn);
            return sendMap(turn);
        }
        if (AID_PF4.equals(turn.aid) && turn.state.isState(TranTypeUpdateState.SHOW_DETAILS)) {
            turn.state.setChangeAction(TranTypeUpdateState.CONFIRM_DELETE);
            return sendMap(turn);
        }
        if (AID_PF5.equals(turn.aid) && turn.state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND)) {
            turn.state.setChangeAction(TranTypeUpdateState.CREATE_NEW_RECORD);
            return sendMap(turn);
        }
        if (AID_PF5.equals(turn.aid)
                && turn.state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED)) {
            writeProcessing(turn);
            return sendMap(turn);
        }
        if (AID_PF12.equals(turn.aid)
                && turn.state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                        TranTypeUpdateState.CONFIRM_DELETE, TranTypeUpdateState.SHOW_DETAILS)) {
            decideAction(turn);
            return sendMap(turn);
        }
        if (turn.invalidKey) {
            // COTRTUPC.cbl:539-542 - repaint with "Invalid key pressed".
            return sendMap(turn);
        }

        processInputs(turn);
        decideAction(turn);
        return sendMap(turn);
    }

    // ------------------------------------------------------- 0001-CHECK-PFKEYS

    /** {@code 0001-CHECK-PFKEYS} (COTRTUPC.cbl:577-618). */
    private void checkPfKeys(Turn turn) {
        TranTypeUpdateState state = turn.state;
        boolean valid = AID_PF3.equals(turn.aid)
                || (AID_ENTER.equals(turn.aid) && !state.isState(TranTypeUpdateState.CONFIRM_DELETE))
                || (AID_PF4.equals(turn.aid) && state.isState(TranTypeUpdateState.SHOW_DETAILS,
                        TranTypeUpdateState.CONFIRM_DELETE))
                || (AID_PF5.equals(turn.aid)
                        && (state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                                TranTypeUpdateState.DETAILS_NOT_FOUND)
                                || state.isDeleteInProgress()))
                || (AID_PF12.equals(turn.aid) && state.isState(
                        TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                        TranTypeUpdateState.SHOW_DETAILS,
                        TranTypeUpdateState.DETAILS_NOT_FOUND,
                        TranTypeUpdateState.CONFIRM_DELETE,
                        TranTypeUpdateState.CREATE_NEW_RECORD));
        if (!valid) {
            turn.invalidKey = true;
            turn.setMessage(TranTypeMessages.UPD_INVALID_KEY);
        }
    }

    /**
     * COTRTUPC.cbl:405-419: a conversation that has finished — committed,
     * failed, deleted, or cancelled from a screen that has no pending work —
     * starts again from the empty key screen on the next keystroke.
     */
    private void simulateInitialEntry(Turn turn) {
        TranTypeUpdateState state = turn.state;
        boolean restart = (AID_PF12.equals(turn.aid) && state.isState(TranTypeUpdateState.SHOW_DETAILS,
                TranTypeUpdateState.CREATE_NEW_RECORD, TranTypeUpdateState.DETAILS_NOT_FOUND))
                || state.isState(TranTypeUpdateState.CHANGES_DONE)
                || state.isChangesFailed()
                || (state.isState(TranTypeUpdateState.CHANGES_BACKED_OUT) && state.isOldDetailsEmpty())
                || state.isState(TranTypeUpdateState.DELETE_DONE, TranTypeUpdateState.DELETE_FAILED);
        if (restart) {
            turn.programEnter = true;
            state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
        }
    }

    // ---------------------------------------------------- 1000-PROCESS-INPUTS

    private void processInputs(Turn turn) {
        storeMapInNew(turn);
        editMapInputs(turn);
    }

    /** {@code 1150-STORE-MAP-IN-NEW} (COTRTUPC.cbl:652-688). */
    private void storeMapInNew(Turn turn) {
        if (turn.state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND)
                && !AID_PF5.equals(turn.aid)
                && safe(turn.typeCodeIn).trim().equals(turn.state.getNewTypeCode())) {
            return;
        }
        turn.state.setNewTypeCode(validator.normalizeInput(turn.typeCodeIn));
        turn.state.setNewDescription(validator.normalizeInput(turn.descriptionIn));
    }

    /** {@code 1200-EDIT-MAP-INPUTS} (COTRTUPC.cbl:689-781). */
    private void editMapInputs(Turn turn) {
        TranTypeUpdateState state = turn.state;

        if (state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND)
                && safe(turn.typeCodeIn).trim().equals(state.getNewTypeCode())) {
            if (!AID_PF5.equals(turn.aid)) {
                state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
            }
            turn.typeFilterValid = true;
            return;
        }

        if (!state.isState(TranTypeUpdateState.CREATE_NEW_RECORD,
                TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED)) {
            editTranType(turn);

            if (turn.typeFilterBlank) {
                // The "No input received" literal is unreachable: 1245-EDIT-NUM-REQD
                // has already put "Tran Type code must be supplied." on the line
                // (FR-U31).
                turn.setMessage(TranTypeMessages.UPD_NO_INPUT);
                state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
                return;
            }
            if (turn.typeFilterNotOk) {
                // COTRTUPC.cbl:728-731 sets TTUP-INVALID-SEARCH-KEYS and then
                // immediately overwrites it with TTUP-DETAILS-NOT-FETCHED, so the
                // 'K' state is never seen (FR-U32).
                state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
                return;
            }
            if (state.isState(TranTypeUpdateState.NOT_FETCHED)) {
                return;
            }
        }

        turn.typeFilterValid = true;
        compareOldNew(turn);

        if (turn.noChangesFound
                || state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                        TranTypeUpdateState.CHANGES_DONE)) {
            return;
        }

        state.setChangeAction(TranTypeUpdateState.CHANGES_NOT_OK);

        String descriptionError = validator.validateDescription(state.getNewDescription());
        if (descriptionError != null) {
            turn.inputError = true;
            turn.setMessage(descriptionError);
        }

        if (!turn.inputError) {
            state.setChangeAction(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED);
        }
    }

    /** {@code 1205-COMPARE-OLD-NEW} (COTRTUPC.cbl:783-816). */
    private void compareOldNew(Turn turn) {
        TranTypeUpdateState state = turn.state;
        String newDesc = state.getNewDescription().trim();
        String oldDesc = state.getOldDescription().trim();

        if (state.getNewTypeCode().equalsIgnoreCase(state.getOldTypeCode())
                && newDesc.equalsIgnoreCase(oldDesc)
                && newDesc.length() == oldDesc.length()) {
            turn.noChangesFound = true;
            turn.setMessage(TranTypeMessages.UPD_NO_CHANGE_DETECTED);
        } else {
            turn.noChangesFound = false;
        }
    }

    /** {@code 1210-EDIT-TRANTYPE} (COTRTUPC.cbl:820-847). */
    private void editTranType(Turn turn) {
        String input = turn.state.getNewTypeCode();
        String error = validator.validateTypeCode(input);

        if (TranTypeMessages.TYPE_CODE_REQUIRED.equals(error)) {
            turn.inputError = true;
            turn.typeFilterBlank = true;
            turn.setMessage(error);
            return;
        }
        if (error != null) {
            turn.inputError = true;
            turn.typeFilterNotOk = true;
            turn.setMessage(error);
            return;
        }
        turn.typeFilterValid = true;
        turn.state.setNewTypeCode(validator.normalizeTypeCode(input));
    }

    // ----------------------------------------------------- 2000-DECIDE-ACTION

    /** {@code 2000-DECIDE-ACTION} (COTRTUPC.cbl:978-1085). */
    private void decideAction(Turn turn) {
        TranTypeUpdateState state = turn.state;

        if (state.isState(TranTypeUpdateState.NOT_FETCHED) || AID_PF12.equals(turn.aid)) {
            if (turn.typeFilterValid) {
                turn.returnMessage = null;
                readTranType(turn);
                state.setChangeAction(turn.foundInTable
                        ? TranTypeUpdateState.SHOW_DETAILS
                        : TranTypeUpdateState.DETAILS_NOT_FOUND);
            } else if (state.isState(TranTypeUpdateState.CONFIRM_DELETE)) {
                turn.setMessage(TranTypeMessages.UPD_DELETE_CANCELLED);
                state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
            } else if (state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED)) {
                turn.setMessage(TranTypeMessages.UPD_UPDATE_CANCELLED);
                state.setChangeAction(TranTypeUpdateState.CHANGES_BACKED_OUT);
            } else {
                state.setChangeAction(TranTypeUpdateState.NOT_FETCHED);
            }
            return;
        }

        if (state.isState(TranTypeUpdateState.SHOW_DETAILS)) {
            if (!turn.inputError && !turn.noChangesFound) {
                state.setChangeAction(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED);
            }
            return;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_NOT_OK,
                TranTypeUpdateState.INVALID_SEARCH_KEYS,
                TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED)) {
            return;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_BACKED_OUT)) {
            state.setChangeAction(TranTypeUpdateState.CHANGES_NOT_OK);
            return;
        }
        if (AID_PF5.equals(turn.aid) && state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND)) {
            state.setChangeAction(TranTypeUpdateState.CREATE_NEW_RECORD);
            return;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_DONE)) {
            state.setChangeAction(TranTypeUpdateState.SHOW_DETAILS);
            return;
        }
        throw new TranTypeAbendException(THIS_PGM, "0001",
                TranTypeAbendException.UNEXPECTED_DATA_SCENARIO);
    }

    // -------------------------------------------------------- table access

    /**
     * {@code 9100-GET-TRANSACTION-TYPE} plus {@code 9500-STORE-FETCHED-DATA}
     * (COTRTUPC.cbl:1469-1527).
     */
    private void readTranType(Turn turn) {
        TranTypeUpdateState state = turn.state;
        state.setOldTypeCode("");
        state.setOldDescription("");

        Optional<Db2TransactionTypeRecord> found =
                transactionTypeRepository.findById(state.getNewTypeCode());
        if (found.isEmpty()) {
            turn.foundInTable = false;
            turn.inputError = true;
            turn.typeFilterNotOk = true;
            turn.setMessage(TranTypeMessages.UPD_NOT_FOUND);
            return;
        }
        turn.foundInTable = true;
        state.setOldTypeCode(found.get().getTrType());
        state.setOldDescription(rtrim(found.get().getTrDescription()));
    }

    /**
     * {@code 9600-WRITE-PROCESSING} and {@code 9700-INSERT-RECORD}
     * (COTRTUPC.cbl:1531-1622): the UPDATE is issued first and SQLCODE +100
     * falls through to the INSERT, which is how the add path saves (FR-U20).
     */
    private void writeProcessing(Turn turn) {
        TranTypeUpdateState state = turn.state;
        String typeCode = state.getNewTypeCode();
        String description = state.getNewDescription().trim();

        Optional<Db2TransactionTypeRecord> existing = transactionTypeRepository.findById(typeCode);
        if (existing.isPresent()) {
            Db2TransactionTypeRecord record = existing.get();
            record.setTrDescription(description);
            transactionTypeRepository.save(record);
        } else {
            Db2TransactionTypeRecord record = new Db2TransactionTypeRecord();
            record.setTrType(typeCode);
            record.setTrDescription(description);
            transactionTypeRepository.save(record);
        }
        state.setChangeAction(TranTypeUpdateState.CHANGES_DONE);
    }

    /** {@code 9800-DELETE-PROCESSING} (COTRTUPC.cbl:1624-1666). */
    private void deleteProcessing(Turn turn) {
        TranTypeUpdateState state = turn.state;
        String typeCode = state.getOldTypeCode();

        if (categoryLookupRepository.existsForTypeCode(typeCode)) {
            // SQLCODE -532: the state stays TTUP-START-DELETE, as in the COBOL.
            turn.setMessage(TranTypeMessages.withSqlCode(TranTypeMessages.UPD_DELETE_HAS_CHILDREN, -532));
            return;
        }
        if (!transactionTypeRepository.existsById(typeCode)) {
            turn.setMessage(TranTypeMessages.withSqlCode(TranTypeMessages.UPD_DELETE_FAILED, 100));
            state.setChangeAction(TranTypeUpdateState.DELETE_FAILED);
            return;
        }
        transactionTypeRepository.deleteById(typeCode);
        state.setChangeAction(TranTypeUpdateState.DELETE_DONE);
    }

    // ---------------------------------------------------------- 3000-SEND-MAP

    private TranTypeUpdateResponse sendMap(Turn turn) {
        TranTypeUpdateState state = turn.state;
        String typeCode = "";
        String description = "";

        if (!turn.programEnter) {
            if (state.isState(TranTypeUpdateState.NOT_FETCHED)) {
                // 3201-SHOW-INITIAL-VALUES
                typeCode = "";
                description = "";
            } else if (state.isState(TranTypeUpdateState.SHOW_DETAILS,
                    TranTypeUpdateState.CONFIRM_DELETE, TranTypeUpdateState.DELETE_FAILED,
                    TranTypeUpdateState.DELETE_DONE, TranTypeUpdateState.CHANGES_BACKED_OUT)) {
                // 3202-SHOW-ORIGINAL-VALUES
                state.setNewTypeCode("");
                state.setNewDescription("");
                typeCode = state.getOldTypeCode();
                description = state.getOldDescription();
            } else if (state.isChangesMade()
                    || state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND,
                            TranTypeUpdateState.INVALID_SEARCH_KEYS,
                            TranTypeUpdateState.CREATE_NEW_RECORD,
                            TranTypeUpdateState.CHANGES_DONE)) {
                // 3203-SHOW-UPDATED-VALUES
                typeCode = state.getNewTypeCode();
                description = state.getNewDescription();
            } else {
                state.setNewTypeCode("");
                state.setNewDescription("");
                typeCode = state.getOldTypeCode();
                description = state.getOldDescription();
            }
        }

        if (turn.typeFilterBlank && state.isProgramReenter()) {
            // COTRTUPC.cbl:1336-1340 - the blank key comes back as a red '*'.
            typeCode = "*";
        }

        boolean typeCodeEditable;
        boolean descriptionEditable;
        if (state.isState(TranTypeUpdateState.NOT_FETCHED, TranTypeUpdateState.INVALID_SEARCH_KEYS,
                TranTypeUpdateState.DETAILS_NOT_FOUND)
                || (state.isState(TranTypeUpdateState.CHANGES_BACKED_OUT) && state.isOldDetailsEmpty())) {
            typeCodeEditable = true;
            descriptionEditable = false;
        } else if (state.isState(TranTypeUpdateState.SHOW_DETAILS,
                TranTypeUpdateState.CHANGES_NOT_OK, TranTypeUpdateState.CREATE_NEW_RECORD,
                TranTypeUpdateState.CHANGES_BACKED_OUT)) {
            typeCodeEditable = false;
            descriptionEditable = true;
        } else if (state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                TranTypeUpdateState.CHANGES_DONE) || state.isDeleteInProgress()) {
            typeCodeEditable = false;
            descriptionEditable = false;
        } else {
            typeCodeEditable = true;
            descriptionEditable = false;
        }

        return new TranTypeUpdateResponse(
                typeCode,
                description,
                setupInfoMessage(turn),
                turn.returnMessage == null ? "" : turn.returnMessage,
                typeCodeEditable,
                descriptionEditable,
                !state.isState(TranTypeUpdateState.CONFIRM_DELETE),
                state.isState(TranTypeUpdateState.SHOW_DETAILS, TranTypeUpdateState.CONFIRM_DELETE),
                state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                        TranTypeUpdateState.DETAILS_NOT_FOUND),
                state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED,
                        TranTypeUpdateState.SHOW_DETAILS, TranTypeUpdateState.DETAILS_NOT_FOUND,
                        TranTypeUpdateState.CONFIRM_DELETE, TranTypeUpdateState.CREATE_NEW_RECORD),
                THIS_PGM,
                THIS_TRANID,
                state);
    }

    /**
     * {@code 3250-SETUP-INFOMSG} (COTRTUPC.cbl:1210-1247). The
     * {@code TTUP-SHOW-DETAILS} case shares the body of the next {@code WHEN},
     * so a fetched record shows "Enter transaction type to be maintained" and
     * the "Selected transaction type shown above" literal is dead (FR-U33).
     */
    private String setupInfoMessage(Turn turn) {
        TranTypeUpdateState state = turn.state;

        if (turn.programEnter
                || state.isState(TranTypeUpdateState.NOT_FETCHED,
                        TranTypeUpdateState.INVALID_SEARCH_KEYS,
                        TranTypeUpdateState.SHOW_DETAILS)) {
            return TranTypeMessages.UPD_INFO_ENTER_KEY;
        }
        if (state.isState(TranTypeUpdateState.DETAILS_NOT_FOUND)) {
            return TranTypeMessages.UPD_INFO_PRESS_F5_TO_ADD;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_BACKED_OUT)) {
            return state.isOldDetailsEmpty()
                    ? TranTypeMessages.UPD_INFO_ENTER_KEY
                    : TranTypeMessages.UPD_INFO_UPDATE_DETAILS;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_NOT_OK)) {
            return TranTypeMessages.UPD_INFO_UPDATE_DETAILS;
        }
        if (state.isState(TranTypeUpdateState.CONFIRM_DELETE)) {
            return TranTypeMessages.UPD_INFO_CONFIRM_DELETE;
        }
        if (state.isState(TranTypeUpdateState.DELETE_FAILED)) {
            return TranTypeMessages.UPD_INFO_CHANGES_UNSUCCESSFUL;
        }
        if (state.isState(TranTypeUpdateState.DELETE_DONE)) {
            return TranTypeMessages.UPD_INFO_DELETE_SUCCESS;
        }
        if (state.isState(TranTypeUpdateState.CREATE_NEW_RECORD)) {
            return TranTypeMessages.UPD_INFO_ENTER_NEW_DETAILS;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED)) {
            return TranTypeMessages.UPD_INFO_CHANGES_VALIDATED;
        }
        if (state.isState(TranTypeUpdateState.CHANGES_DONE)) {
            return TranTypeMessages.UPD_INFO_CHANGES_COMMITTED;
        }
        if (state.isChangesFailed()) {
            return TranTypeMessages.UPD_INFO_CHANGES_UNSUCCESSFUL;
        }
        return TranTypeMessages.UPD_INFO_ENTER_KEY;
    }

    private TranTypeUpdateResponse transfer() {
        return new TranTypeUpdateResponse("", "", "", "", false, false, false, false, false, false,
                ADMIN_PGM, ADMIN_TRANID, TranTypeUpdateState.firstEntry());
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

    /** The working storage of one CTTU task ({@code WS-MISC-STORAGE}). */
    private static final class Turn {

        private TranTypeUpdateState state;
        private final String aid;
        private final String typeCodeIn;
        private final String descriptionIn;

        private String returnMessage;
        private boolean programEnter;
        private boolean invalidKey;
        private boolean inputError;
        private boolean typeFilterValid;
        private boolean typeFilterBlank;
        private boolean typeFilterNotOk;
        private boolean noChangesFound;
        private boolean foundInTable;

        private Turn(TranTypeUpdateRequest request) {
            boolean firstEntry = request.state() == null;
            this.state = firstEntry ? TranTypeUpdateState.firstEntry() : request.state().copy();
            this.programEnter = firstEntry;
            this.aid = safe(request.aid()).trim().toUpperCase();
            this.typeCodeIn = request.typeCode();
            this.descriptionIn = request.description();
        }

        /** {@code IF WS-RETURN-MSG-OFF ... END-IF}: the first message wins. */
        private void setMessage(String message) {
            if (returnMessage == null) {
                returnMessage = message;
            }
        }
    }
}
