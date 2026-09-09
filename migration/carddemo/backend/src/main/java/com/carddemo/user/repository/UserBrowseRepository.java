package com.carddemo.user.repository;

import com.carddemo.common.domain.SecUserRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * The CU00 keyed browse over the shared {@link SecUserRecord} entity.
 *
 * <p>The shared {@code com.carddemo.common.repository.SecUserRepository} is a
 * plain {@code JpaRepository} and carries the CRUD this stream needs for
 * CU01/CU02/CU03; it is used unchanged. The ordered/cursor derived queries that
 * stand in for {@code STARTBR}/{@code READNEXT}/{@code READPREV}
 * (COUSR00C.cbl:586-682) are declared here, inside the stream's own package, so
 * no shared file is modified.
 */
public interface UserBrowseRepository extends Repository<SecUserRecord, String> {

    /** STARTBR LOW-VALUES + READNEXT: the first page from the top of USRSEC. */
    List<SecUserRecord> findByOrderBySecUsrIdAsc(Pageable pageable);

    /** STARTBR GTEQ + READNEXT: the page beginning at/after the search key (inclusive). */
    List<SecUserRecord> findBySecUsrIdGreaterThanEqualOrderBySecUsrIdAsc(String userId, Pageable pageable);

    /** PF8: the page after the last id shown (the throwaway READNEXT, cbl:286-288). */
    List<SecUserRecord> findBySecUsrIdGreaterThanOrderBySecUsrIdAsc(String userId, Pageable pageable);

    /** PF7: READPREV from the first id shown, newest-first; the service reverses it. */
    List<SecUserRecord> findBySecUsrIdLessThanOrderBySecUsrIdDesc(String userId, Pageable pageable);
}
