package com.carddemo.batch.filereads;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The datasets the S-15 jobs allocate, mapped onto files under
 * {@code carddemo.batch.output-dir}. The names are the DSNs of the JCL
 * (`app/jcl/READACCT.jcl:23-27,37-48`) so a migrated run is recognisable to whoever
 * knew the mainframe job.
 */
@Component
public class FileReadOutputs {

    private final Path outputDir;

    public FileReadOutputs(@Value("${carddemo.batch.output-dir}") String outputDir) {
        this.outputDir = Path.of(outputDir);
    }

    public Path directory() {
        return outputDir;
    }

    /** {@code SYSOUT} of the named job, e.g. {@code READACCT.SYSOUT.txt}. */
    public Path sysout(String jobName) {
        return outputDir.resolve(jobName + ".SYSOUT.txt");
    }

    /** {@code OUTFILE} — AWS.M2.CARDDEMO.ACCTDATA.PSCOMP, LRECL 107 RECFM FB. */
    public Path accountExtract() {
        return outputDir.resolve("AWS.M2.CARDDEMO.ACCTDATA.PSCOMP");
    }

    /** {@code ARRYFILE} — AWS.M2.CARDDEMO.ACCTDATA.ARRYPS, LRECL 110 RECFM FB. */
    public Path accountArray() {
        return outputDir.resolve("AWS.M2.CARDDEMO.ACCTDATA.ARRYPS");
    }

    /** {@code VBRCFILE} — AWS.M2.CARDDEMO.ACCTDATA.VBPS, LRECL 84 RECFM VB. */
    public Path accountVariable() {
        return outputDir.resolve("AWS.M2.CARDDEMO.ACCTDATA.VBPS");
    }
}
