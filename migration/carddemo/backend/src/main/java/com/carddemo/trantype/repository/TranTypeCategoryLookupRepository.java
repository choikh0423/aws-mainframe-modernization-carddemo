package com.carddemo.trantype.repository;

import com.carddemo.common.domain.Db2TransactionTypeCategoryRecord;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Stream-private lookup used to reproduce DB2 {@code SQLCODE -532} — the
 * referential-integrity refusal both delete paths report as "Please delete
 * associated child records first:" (COTRTLIC.cbl:1917-1925,
 * COTRTUPC.cbl:1643-1650). The legacy programs learned about the child rows
 * from the failing DELETE; the migrated code checks the same relationship
 * ahead of the delete so the message does not depend on the JDBC driver's
 * vendor code.
 */
public interface TranTypeCategoryLookupRepository
        extends Repository<Db2TransactionTypeCategoryRecord, String> {

    /** True when TRANSACTION_TYPE_CATEGORY still has rows for this type code. */
    @Query("select count(c) > 0 from Db2TransactionTypeCategoryRecord c"
            + " where c.id.trcTypeCode = :typeCode")
    boolean existsForTypeCode(@Param("typeCode") String typeCode);
}
