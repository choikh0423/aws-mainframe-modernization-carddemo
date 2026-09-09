package com.carddemo.pendingauth.repository;

import com.carddemo.common.domain.PendingAuthSummaryRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The root-segment browse the S-09 batch programs perform with
 * {@code GN PAUTSUM0}: forward through the database in key order.
 *
 * <p>Stream-private (S-09 owns it); the shared
 * {@code PendingAuthSummaryRepository} keeps the plain CRUD contract.
 */
@Repository
public interface PendingAuthSummaryBrowseRepository
        extends JpaRepository<PendingAuthSummaryRecord, Long> {

    /**
     * {@code GN PAUTSUM0} from the start of the database, keyset-paged so that
     * deleting roots as the browse advances cannot make it skip a root.
     */
    @Query("select s from PendingAuthSummaryRecord s "
            + "where s.paAcctId > :afterAcctId order by s.paAcctId asc")
    List<PendingAuthSummaryRecord> browseAfter(@Param("afterAcctId") Long afterAcctId,
                                               Pageable page);
}
