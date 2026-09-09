package com.carddemo.trantype.service;

import com.carddemo.common.domain.Db2TransactionTypeRecord;
import com.carddemo.common.repository.Db2TransactionTypeRepository;
import com.carddemo.trantype.dto.TranTypeListRequest;
import com.carddemo.trantype.dto.TranTypeListResponse;
import com.carddemo.trantype.dto.TranTypeListRow;
import com.carddemo.trantype.dto.TranTypeListRowInput;
import com.carddemo.trantype.dto.TranTypeListState;
import com.carddemo.trantype.dto.TranTypeUpdateRequest;
import com.carddemo.trantype.dto.TranTypeUpdateResponse;
import com.carddemo.trantype.dto.TranTypeUpdateState;
import com.carddemo.trantype.message.TranTypeMessages;
import com.carddemo.trantype.repository.TranTypeBrowseRepository;
import com.carddemo.trantype.repository.TranTypeCategoryLookupRepository;
import com.carddemo.trantype.validator.TranTypeValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The DB2 {@code SQLCODE -911} branches of {@code 9200-UPDATE-RECORD}
 * (COTRTLIC.cbl:1874-1883) and {@code 9600-WRITE-PROCESSING}
 * (COTRTUPC.cbl:1561-1566). A row lock cannot be forced from H2 inside one
 * thread, so the repository is stubbed to raise the exception Spring maps a
 * lock timeout to.
 *
 * <p>Covers FR-L24 (deadlock leg) and FR-U23.
 */
class TranTypeLockFailureTest {

    private final Db2TransactionTypeRepository transactionTypes = mock(Db2TransactionTypeRepository.class);
    private final TranTypeCategoryLookupRepository categories =
            mock(TranTypeCategoryLookupRepository.class);
    private final TranTypeBrowseRepository browse = mock(TranTypeBrowseRepository.class);
    private final TranTypeValidator validator = new TranTypeValidator();

    @Test
    void aLockedRowLeavesTheListUpdateRequestedWithTheDeadlockMessage() {
        Db2TransactionTypeRecord record = record("01", "Purchase");
        when(transactionTypes.findById("01")).thenReturn(Optional.of(record));
        when(transactionTypes.save(any(Db2TransactionTypeRecord.class)))
                .thenThrow(new CannotAcquireLockException("SQLCODE -911"));
        when(browse.readForward(anyString(), any(), any(), any())).thenReturn(List.of(record));

        TranTypeListService service =
                new TranTypeListService(browse, transactionTypes, categories, validator);

        TranTypeListState state = new TranTypeListState();
        state.setRows(new ArrayList<>(List.of(new TranTypeListRow("01", "Purchase"))));
        state.setScreenNum(1);
        state.setFirstTypeCode("01");
        state.setLastTypeCode("01");
        state.setRowSelected(1);
        state.setUpdateRequested(true);

        List<TranTypeListRowInput> rows = new ArrayList<>();
        rows.add(new TranTypeListRowInput("U", "Purchase two"));
        for (int i = 1; i < TranTypeListService.MAX_SCREEN_LINES; i++) {
            rows.add(TranTypeListRowInput.blank());
        }

        TranTypeListResponse response =
                service.handle(new TranTypeListRequest("PF10", "", "", rows, state));

        assertThat(response.errorMessage())
                .isEqualTo(TranTypeMessages.withSqlCode(TranTypeMessages.LIST_UPDATE_DEADLOCK, -911));
        assertThat(response.state().isUpdateRequested()).isTrue();
    }

    @Test
    void aLockedRowLeavesTheMaintenanceScreenInTheLockErrorState() {
        when(transactionTypes.findById("01")).thenReturn(Optional.of(record("01", "Purchase")));
        when(transactionTypes.save(any(Db2TransactionTypeRecord.class)))
                .thenThrow(new CannotAcquireLockException("SQLCODE -911"));

        TranTypeUpdateService service =
                new TranTypeUpdateService(transactionTypes, categories, validator);

        TranTypeUpdateState state = new TranTypeUpdateState();
        state.setProgramReenter(true);
        state.setChangeAction(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED);
        state.setOldTypeCode("01");
        state.setOldDescription("Purchase");
        state.setNewTypeCode("01");
        state.setNewDescription("Purchase two");

        TranTypeUpdateResponse response =
                service.handle(new TranTypeUpdateRequest("PF5", "01", "Purchase two", state));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.UPD_COULD_NOT_LOCK);
        assertThat(response.state().getChangeAction())
                .isEqualTo(TranTypeUpdateState.CHANGES_LOCK_ERROR);
    }

    private static Db2TransactionTypeRecord record(String type, String description) {
        Db2TransactionTypeRecord record = new Db2TransactionTypeRecord();
        record.setTrType(type);
        record.setTrDescription(description);
        return record;
    }
}
