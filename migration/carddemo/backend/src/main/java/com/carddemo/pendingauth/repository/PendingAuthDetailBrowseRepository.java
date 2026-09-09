package com.carddemo.pendingauth.repository;

import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The stream-private browse of the flattened PAUTDTL1 children (B-07).
 *
 * <p>Every query orders by the child sequence field {@code PAUT9CTS}
 * ({@code paAuthDate9c}, {@code paAuthTime9c}) ascending, which is the order
 * {@code GNP PAUTDTL1} returns segments in, so the migrated screens see exactly
 * the sequence the legacy programs paged through. The shared
 * {@code PendingAuthDetailRepository} keeps plain CRUD; the paging predicates
 * live here, in the stream's own package.
 */
@Repository
public interface PendingAuthDetailBrowseRepository
        extends JpaRepository<PendingAuthDetailRecord, PendingAuthDetailId> {

    /**
     * The whole child chain of one root, in {@code GNP} order: the loop
     * CBPAUP0C and PAUDBUNL run until {@code GE}.
     */
    @Query("select d from PendingAuthDetailRecord d where d.id.paAcctId = :acctId"
            + " order by d.id.paAuthDate9c asc, d.id.paAuthTime9c asc")
    List<PendingAuthDetailRecord> browseChildren(@Param("acctId") Long acctId);

    /** {@code GU PAUTSUM0} + first {@code GNP PAUTDTL1}: the top of the chain. */
    @Query("select d from PendingAuthDetailRecord d where d.id.paAcctId = :acctId"
            + " order by d.id.paAuthDate9c asc, d.id.paAuthTime9c asc")
    List<PendingAuthDetailRecord> browse(@Param("acctId") Long acctId, Pageable page);

    /**
     * {@code REPOSITION-AUTHORIZATIONS} (GNP WHERE PAUT9CTS = key) followed by
     * further {@code GNP}s: the page that starts at the given key
     * (COPAUS0C.cbl:487-510, used by PF7).
     */
    @Query("select d from PendingAuthDetailRecord d where d.id.paAcctId = :acctId"
            + " and (d.id.paAuthDate9c > :date9c"
            + "   or (d.id.paAuthDate9c = :date9c and d.id.paAuthTime9c >= :time9c))"
            + " order by d.id.paAuthDate9c asc, d.id.paAuthTime9c asc")
    List<PendingAuthDetailRecord> browseFrom(@Param("acctId") Long acctId,
                                             @Param("date9c") Integer date9c,
                                             @Param("time9c") Long time9c,
                                             Pageable page);

    /**
     * The {@code GNP} that follows a repositioning read: the segments after the
     * given key (COPAUS0C PF8, COPAUS1C PF8).
     */
    @Query("select d from PendingAuthDetailRecord d where d.id.paAcctId = :acctId"
            + " and (d.id.paAuthDate9c > :date9c"
            + "   or (d.id.paAuthDate9c = :date9c and d.id.paAuthTime9c > :time9c))"
            + " order by d.id.paAuthDate9c asc, d.id.paAuthTime9c asc")
    List<PendingAuthDetailRecord> browseAfter(@Param("acctId") Long acctId,
                                              @Param("date9c") Integer date9c,
                                              @Param("time9c") Long time9c,
                                              Pageable page);
}
