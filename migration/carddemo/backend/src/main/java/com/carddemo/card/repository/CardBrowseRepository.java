package com.carddemo.card.repository;

import com.carddemo.common.domain.CardRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stream-private CARDDAT access for S-03. The shared
 * {@link com.carddemo.common.repository.CardRepository} exposes CRUD only, so
 * the key ranges COCRDLIC drives through STARTBR/READNEXT/READPREV and the
 * READ UPDATE lock COCRDUPC takes live here.
 */
@Repository
public interface CardBrowseRepository extends JpaRepository<CardRecord, String> {

    /** STARTBR GTEQ + READNEXT (COCRDLIC.cbl:1129-1154). */
    List<CardRecord> findByCardNumGreaterThanEqualOrderByCardNumAsc(String cardNum, Pageable pageable);

    /** Continuation of the same READNEXT browse, past the last record read. */
    List<CardRecord> findByCardNumGreaterThanOrderByCardNumAsc(String cardNum, Pageable pageable);

    /** READPREV from the first key of the page, exclusive (COCRDLIC.cbl:1294-1330). */
    List<CardRecord> findByCardNumLessThanOrderByCardNumDesc(String cardNum, Pageable pageable);

    /** READ … UPDATE on CARDDAT: takes the row lock before the rewrite (COCRDUPC.cbl:1427-1436). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardRecord c where c.cardNum = :cardNum")
    Optional<CardRecord> findForUpdate(@Param("cardNum") String cardNum);
}
