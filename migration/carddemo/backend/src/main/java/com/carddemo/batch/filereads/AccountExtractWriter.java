package com.carddemo.batch.filereads;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The three sequential output datasets of CBACT01C: OUTFILE (FB 107), ARRYFILE (FB 110)
 * and VBRCFILE (VB 84).
 *
 * <p>Each variable record is written with the 4-byte RDW that {@code RECORDING MODE V}
 * puts in front of it: the record length including the RDW in the first two bytes, big
 * endian, then two zero bytes (FR-A11).
 *
 * <p>The datasets are opened {@code OUTPUT}, i.e. created or replaced
 * (`CBACT01C.cbl:336,354,372`); the PREDEL step of the JCL has already removed any
 * catalogued copy.
 */
@Component
public class AccountExtractWriter {

    private final FileReadOutputs outputs;

    private OutputStream accountFile;
    private OutputStream arrayFile;
    private OutputStream variableFile;

    public AccountExtractWriter(FileReadOutputs outputs) {
        this.outputs = outputs;
    }

    /** 2000-OUTFILE-OPEN. */
    public void openAccountFile() throws IOException {
        accountFile = create(outputs.accountExtract());
    }

    /** 3000-ARRFILE-OPEN. */
    public void openArrayFile() throws IOException {
        arrayFile = create(outputs.accountArray());
    }

    /** 4000-VBRFILE-OPEN. */
    public void openVariableFile() throws IOException {
        variableFile = create(outputs.accountVariable());
    }

    /** 1350-WRITE-ACCT-RECORD. */
    public void writeAccountRecord(byte[] record) throws IOException {
        accountFile.write(record);
    }

    /** 1450-WRITE-ARRY-RECORD. */
    public void writeArrayRecord(byte[] record) throws IOException {
        arrayFile.write(record);
    }

    /** 1550-WRITE-VB1-RECORD / 1575-WRITE-VB2-RECORD. */
    public void writeVariableRecord(String record) throws IOException {
        byte[] data = record.getBytes(StandardCharsets.ISO_8859_1);
        int length = data.length + 4;
        variableFile.write(new byte[]{(byte) (length >> 8), (byte) length, 0, 0});
        variableFile.write(data);
    }

    /** Releases the datasets at the end of the step. */
    public void close() throws IOException {
        IOException failure = null;
        for (OutputStream stream : new OutputStream[]{accountFile, arrayFile, variableFile}) {
            if (stream == null) {
                continue;
            }
            try {
                stream.close();
            } catch (IOException e) {
                failure = e;
            }
        }
        accountFile = null;
        arrayFile = null;
        variableFile = null;
        if (failure != null) {
            throw failure;
        }
    }

    private OutputStream create(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.newOutputStream(path);
    }
}
