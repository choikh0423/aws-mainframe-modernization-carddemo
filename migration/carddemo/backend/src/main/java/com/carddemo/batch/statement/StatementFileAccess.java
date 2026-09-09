package com.carddemo.batch.statement;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * CBSTM03B, app/cbl/CBSTM03B.CBL: the file-access subroutine CBSTM03A calls for
 * every TRNXFILE / XREFFILE / CUSTFILE / ACCTFILE operation. The DD name selects
 * the file, the operation selects open / close / sequential read / keyed read,
 * and the FILE STATUS of the operation comes back in the return code
 * (CBSTM03B.CBL:118-131, 151-152, 176, 201, 226).
 *
 * <p>Sequential reads are served from the store in key order and paged, matching
 * ORGANIZATION INDEXED ACCESS SEQUENTIAL. Like the COBOL subroutine, an instance
 * holds the open cursors, so a single caller drives it at a time - which is what
 * step STEP040 does.
 */
@Component
public class StatementFileAccess {

    private static final int PAGE_SIZE = 200;

    private enum SequentialFile {
        TRNXFILE, XREFFILE
    }

    private final StatementWorkTransactionRepository workTransactions;
    private final CardXrefRepository cardXrefs;
    private final CustomerRepository customers;
    private final AccountRepository accounts;

    private final Map<SequentialFile, Cursor> cursors = new EnumMap<>(SequentialFile.class);

    public StatementFileAccess(StatementWorkTransactionRepository workTransactions,
                               CardXrefRepository cardXrefs,
                               CustomerRepository customers,
                               AccountRepository accounts) {
        this.workTransactions = workTransactions;
        this.cardXrefs = cardXrefs;
        this.customers = customers;
        this.accounts = accounts;
    }

    /** CALL 'CBSTM03B' USING WS-M03B-AREA. */
    public void call(StatementFileArea area) {
        switch (area.getDd() == null ? "" : area.getDd()) {
            case StatementFileArea.DD_TRNXFILE -> sequential(area, SequentialFile.TRNXFILE,
                    pageable -> workTransactions
                            .findAll(sorted(pageable, "cardNum", "tranId"))
                            .map(TrnxLayout::render)
                            .getContent());
            case StatementFileArea.DD_XREFFILE -> sequential(area, SequentialFile.XREFFILE,
                    pageable -> cardXrefs
                            .findAll(sorted(pageable, "cardNum"))
                            .map(XrefLayout::render)
                            .getContent());
            case StatementFileArea.DD_CUSTFILE -> keyed(area,
                    key -> customers.findById(Long.parseLong(key)).map(CustomerLayout::render));
            case StatementFileArea.DD_ACCTFILE -> keyed(area,
                    key -> accounts.findById(Long.parseLong(key)).map(AccountLayout::render));
            default -> {
                // WHEN OTHER GO TO 9999-GOBACK (CBSTM03B.CBL:127-128): no return code is set.
            }
        }
    }

    private static Pageable sorted(Pageable pageable, String... properties) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(properties));
    }

    private void sequential(StatementFileArea area, SequentialFile file,
                            Function<Pageable, List<String>> loader) {
        switch (area.getOper()) {
            case StatementFileArea.OPER_OPEN -> {
                try {
                    Cursor cursor = new Cursor(loader);
                    cursor.fill();
                    cursors.put(file, cursor);
                    area.setRc(StatementFileArea.RC_OK);
                } catch (DataAccessException failure) {
                    area.setRc(StatementFileArea.RC_OPEN_FAILED);
                }
            }
            case StatementFileArea.OPER_READ -> {
                Cursor cursor = cursors.get(file);
                if (cursor == null) {
                    area.setRc(StatementFileArea.RC_ERROR);
                    return;
                }
                try {
                    String record = cursor.next();
                    if (record == null) {
                        area.setRc(StatementFileArea.RC_END_OF_FILE);
                    } else {
                        area.setData(record);
                        area.setRc(StatementFileArea.RC_OK);
                    }
                } catch (DataAccessException failure) {
                    area.setRc(StatementFileArea.RC_ERROR);
                }
            }
            case StatementFileArea.OPER_CLOSE -> {
                cursors.remove(file);
                area.setRc(StatementFileArea.RC_OK);
            }
            default -> {
                // W and Z are declared but unimplemented (CBSTM03B.CBL:107-108, 133-155):
                // the paragraph falls through and the caller keeps the previous status.
            }
        }
    }

    private void keyed(StatementFileArea area, Function<String, Optional<String>> reader) {
        switch (area.getOper()) {
            case StatementFileArea.OPER_OPEN, StatementFileArea.OPER_CLOSE ->
                    area.setRc(StatementFileArea.RC_OK);
            case StatementFileArea.OPER_READ_KEY -> {
                try {
                    Optional<String> record = reader.apply(area.keyValue().trim());
                    if (record.isPresent()) {
                        area.setData(record.get());
                        area.setRc(StatementFileArea.RC_OK);
                    } else {
                        area.setRc(StatementFileArea.RC_NOT_FOUND);
                    }
                } catch (NumberFormatException notAKey) {
                    area.setRc(StatementFileArea.RC_NOT_FOUND);
                } catch (DataAccessException failure) {
                    area.setRc(StatementFileArea.RC_ERROR);
                }
            }
            default -> {
                // As above: unimplemented operations leave the return code untouched.
            }
        }
    }

    /** A paged forward-only read of one indexed file. */
    private static final class Cursor {

        private final Function<Pageable, List<String>> loader;
        private List<String> buffer = new ArrayList<>();
        private int index;
        private int page;
        private boolean exhausted;

        private Cursor(Function<Pageable, List<String>> loader) {
            this.loader = loader;
        }

        private void fill() {
            buffer = loader.apply(PageRequest.of(page, PAGE_SIZE));
            index = 0;
            page++;
            exhausted = buffer.size() < PAGE_SIZE;
        }

        private String next() {
            if (index >= buffer.size()) {
                if (exhausted) {
                    return null;
                }
                fill();
                if (buffer.isEmpty()) {
                    return null;
                }
            }
            return buffer.get(index++);
        }
    }
}
