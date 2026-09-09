package com.carddemo.batch.tranreport;

import com.carddemo.common.domain.TransactionRecord;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The TRANSACT fixture the S-14 tests run on, {@code tranreport/transact.txt}.
 *
 * <p>TRANSACT is the one CardDemo file with no unload under
 * {@code app/data/ASCII} (the seed migration says as much). The fixture is
 * therefore derived from {@code app/data/ASCII/dailytran.txt} - DALYTRAN
 * (CVTRA06Y) and TRANSACT (CVTRA05Y) are the same 350-byte layout - by filling
 * in the TRAN-PROC-TS that posting would have set: the 300 records cycle over
 * {@code 2022-07-01} to {@code 2022-07-09} with every tenth record stamped
 * {@code 2022-08-15}, so a report for {@code 2022-07-01} to {@code 2022-07-06}
 * exercises both sides of the INCLUDE condition. Nothing else is changed.
 *
 * <p>{@code tranreport/CBTRN03C_golden_report.txt} is the report the real
 * CBTRN03C produced from this fixture, compiled with GnuCOBOL 3.1.2 straight
 * from {@code app/cbl/CBTRN03C.cbl} and driven with the JCL's own sort
 * (INCLUDE on TRAN-PROC-DT, SORT on TRAN-CARD-NUM) and the ASCII CARDXREF,
 * TRANTYPE and TRANCATG files, folded into its 133-byte records one per line.
 */
final class TransactFixture {

    private TransactFixture() {
    }

    static List<String> images() {
        return read("tranreport/transact.txt");
    }

    static List<String> goldenReport() {
        return read("tranreport/CBTRN03C_golden_report.txt");
    }

    /** The fixture as {@code transactions} rows, exactly as an unload would restore them. */
    static List<TransactionRecord> rows() {
        return images().stream().map(TransactFixture::row).toList();
    }

    static TransactionRecord row(String image) {
        PostedTransaction parsed = TransactionRecordImage.parse(image);
        TransactionRecord row = new TransactionRecord();
        row.setId(parsed.id());
        row.setTypeCd(parsed.typeCd());
        row.setCatCd(parsed.catCd());
        row.setSource(parsed.source());
        row.setDescription(image.substring(32, 132));
        row.setAmount(parsed.amount());
        row.setMerchantId(Long.parseLong(image.substring(143, 152)));
        row.setMerchantName(image.substring(152, 202));
        row.setMerchantCity(image.substring(202, 252));
        row.setMerchantZip(image.substring(252, 262));
        row.setCardNum(parsed.cardNum());
        row.setOrigTs(image.substring(278, 304));
        row.setProcTs(image.substring(304, 330));
        return row;
    }

    private static List<String> read(String resource) {
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
