package com.carddemo.trantype.repository;

import com.carddemo.common.domain.Db2TransactionTypeRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The stream-private browse of {@code db2_transaction_type} behind CTLI: the
 * JPA form of cursors {@code C-TR-TYPE-FORWARD} and {@code C-TR-TYPE-BACKWARD}
 * and of the filter count query (COTRTLIC.cbl:339-372, 1801-1834).
 *
 * <p>The COBOL cursors switch the filters on with the host variables
 * {@code WS-EDIT-TYPE-FLAG} / {@code WS-EDIT-DESC-FLAG}; here a {@code null}
 * parameter means "filter not supplied", which produces the same predicate.
 * Plain CRUD stays on the shared {@code Db2TransactionTypeRepository}.
 */
public interface TranTypeBrowseRepository extends Repository<Db2TransactionTypeRecord, String> {

    /** C-TR-TYPE-FORWARD: {@code TR_TYPE >= :startKey ... ORDER BY TR_TYPE}. */
    @Query("select t from Db2TransactionTypeRecord t"
            + " where t.trType >= :startKey"
            + "   and (:typeFilter is null or t.trType = :typeFilter)"
            + "   and (:descFilter is null or t.trDescription like :descFilter)"
            + " order by t.trType")
    List<Db2TransactionTypeRecord> readForward(@Param("startKey") String startKey,
                                               @Param("typeFilter") String typeFilter,
                                               @Param("descFilter") String descFilter,
                                               Pageable pageable);

    /** C-TR-TYPE-BACKWARD: {@code TR_TYPE < :startKey ... ORDER BY TR_TYPE DESC}. */
    @Query("select t from Db2TransactionTypeRecord t"
            + " where t.trType < :startKey"
            + "   and (:typeFilter is null or t.trType = :typeFilter)"
            + "   and (:descFilter is null or t.trDescription like :descFilter)"
            + " order by t.trType desc")
    List<Db2TransactionTypeRecord> readBackward(@Param("startKey") String startKey,
                                                @Param("typeFilter") String typeFilter,
                                                @Param("descFilter") String descFilter,
                                                Pageable pageable);

    /** 9100-CHECK-FILTERS: {@code SELECT COUNT(*)} under the same predicate. */
    @Query("select count(t) from Db2TransactionTypeRecord t"
            + " where (:typeFilter is null or t.trType = :typeFilter)"
            + "   and (:descFilter is null or t.trDescription like :descFilter)")
    long countMatching(@Param("typeFilter") String typeFilter,
                       @Param("descFilter") String descFilter);
}
