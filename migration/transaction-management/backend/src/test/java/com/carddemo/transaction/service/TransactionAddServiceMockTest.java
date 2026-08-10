package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.CardXrefResolveResponse;
import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.exception.DuplicateTranIdException;
import com.carddemo.transaction.repository.TransactionRepository;
import com.carddemo.transaction.validator.TransactionValidator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duplicate-key path for {@link TransactionAddService} — the DFHRESP(DUPKEY)
 * branch of WRITE-TRANSACT-FILE (COTRN02C.cbl:735-741). Pure Mockito (no Spring
 * context) so it can force a collision on the generated key without perturbing
 * the shared H2 seed used by the {@code @SpringBootTest} suite.
 */
class TransactionAddServiceMockTest {

    private static TransactionAddRequest validRequest() {
        TransactionAddRequest r = new TransactionAddRequest();
        r.setAccountId("10000000001");
        r.setTypeCd("07");
        r.setCategoryCd("1234");
        r.setSource("POS");
        r.setDescription("NEW TRANSACTION");
        r.setAmount("-00000123.45");
        r.setOrigDate("2023-07-01");
        r.setProcDate("2023-07-02");
        r.setMerchantId("900000009");
        r.setMerchantName("NEW MERCHANT");
        r.setMerchantCity("DENVER");
        r.setMerchantZip("80202");
        r.setConfirm("Y");
        return r;
    }

    @Test
    void duplicateGeneratedKey_throwsTranIdAlreadyExist_andDoesNotSave() {
        TransactionRepository repository = mock(TransactionRepository.class);
        CardXrefResolveService resolveService = mock(CardXrefResolveService.class);
        TransactionValidator validator = new TransactionValidator(new com.carddemo.transaction.service.DateValidationService());
        TransactionAddService service = new TransactionAddService(repository, resolveService, validator);

        when(resolveService.resolve(any(), any()))
                .thenReturn(new CardXrefResolveResponse("10000000001", "4111111111111111"));
        TransactionRecord top = new TransactionRecord();
        top.setId("0000000000000030");
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.of(top));
        // The computed next key (31) already exists -> DFHRESP(DUPKEY) equivalent.
        when(repository.existsById("0000000000000031")).thenReturn(true);

        assertThatThrownBy(() -> service.add(validRequest()))
                .isInstanceOf(DuplicateTranIdException.class)
                .hasMessage("Tran ID already exist...");

        verify(repository, never()).save(any());
    }
}
