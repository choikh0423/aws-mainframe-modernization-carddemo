package com.carddemo.batch.operations;

import java.util.List;

/**
 * The job dependency graph of the two mainframe schedulers, boundary B-14.
 *
 * <p>No scheduler is migrated: the batch jobs expose exit codes only (0 completed,
 * 12 failed - see {@code com.carddemo.batch.BatchJobLauncher}). This class is the
 * machine-readable form of the orchestration contract every migrated batch chain
 * is checked against, and the prose form lives in
 * {@code docs/migration/streams/OperationsChain/OperationsChain_orchestration_contract.md}.
 * Both are kept honest by {@code OperationsChainScheduleTest}, which re-derives the
 * triggers from {@code app/scheduler/CardDemo.controlm} and
 * {@code app/scheduler/CardDemo.ca7} and checks the document lists every one.
 */
public final class OperationsChainSchedule {

    /**
     * One "job B runs after job A" edge.
     *
     * @param chain       the Control-M folder, or the CA-7 chain segment named in the contract document
     * @param predecessor the job whose completion releases the successor
     * @param successor   the job released
     * @param condition   Control-M: the IN/OUT condition name; CA-7: the SCHID of the trigger
     */
    public record JobTrigger(String chain, String predecessor, String successor, String condition) {
    }

    private static final List<JobTrigger> CONTROL_M = List.of(
            new JobTrigger("DAILY-TransactionBackup", "CLOSEFIL", "TRANBKP",
                    "DAILY-TransactionBackup-CLOSEFIL"),
            new JobTrigger("DAILY-TransactionBackup", "TRANBKP", "WAITSTEP",
                    "DAILY-TransactionBackup-TRANBKP"),
            new JobTrigger("DAILY-TransactionBackup", "WAITSTEP", "OPENFIL",
                    "DAILY-TransactionBackup-WAITSTEP"),
            new JobTrigger("WEEKLY-TransactionTypesDBRefresh", "MNTTRDB2", "TRANEXTR",
                    "WEEKLY-TransactionTypesDBRefresh-MNTTRDB2"),
            new JobTrigger("WEEKLY-DisclosureGroupsRefresh", "MNTTRDB2", "CLOSEFIL",
                    "WEEKLY-TransactionTypesDBRefresh-MNTTRDB2"),
            new JobTrigger("WEEKLY-DisclosureGroupsRefresh", "CLOSEFIL", "DISCGRP",
                    "WEEKLY-DisclosureGroupsRefresh-CLOSEFIL"),
            new JobTrigger("WEEKLY-DisclosureGroupsRefresh", "DISCGRP", "WAITSTEP",
                    "WEEKLY-DisclosureGroupsRefresh-DISCGRP"),
            new JobTrigger("WEEKLY-DisclosureGroupsRefresh", "WAITSTEP", "OPENFIL",
                    "WEEKLY-DisclosureGroupsRefresh-WAITSTEP"),
            new JobTrigger("MONTHLY-InterestCalculation", "CLOSEFIL", "INTCALC",
                    "MONTHLY-InterestCalculation-CLOSEFIL"),
            new JobTrigger("MONTHLY-InterestCalculation", "INTCALC", "COMBTRAN",
                    "MONTHLY-InterestCalculation-INTCALC"),
            new JobTrigger("MONTHLY-InterestCalculation", "COMBTRAN", "WAITSTEP",
                    "MONTHLY-InterestCalculation-COMBTRAN"),
            new JobTrigger("MONTHLY-InterestCalculation", "WAITSTEP", "OPENFIL",
                    "MONTHLY-InterestCalculation-WAITSTEP"));

    private static final List<JobTrigger> CA7 = List.of(
            new JobTrigger("Posting", "CLOSEFIL", "CBPAUP0J", "030"),
            new JobTrigger("Posting", "CBPAUP0J", "POSTTRAN", "030"),
            new JobTrigger("Posting", "POSTTRAN", "WAITSTEP", "030"),
            new JobTrigger("Posting", "WAITSTEP", "OPENFIL", "030"),
            new JobTrigger("ReferenceDataReload", "CLOSEFIL", "TRANTYPE", "030"),
            new JobTrigger("ReferenceDataReload", "TRANTYPE", "WAITSTEP", "030"),
            new JobTrigger("ReferenceDataReload", "WAITSTEP", "CLOSEFIL1", "031"),
            new JobTrigger("ReferenceDataReload", "WAITSTEP", "CLOSEFIL2", "032"),
            new JobTrigger("ReferenceDataReload", "CLOSEFIL1", "TRANCATG", "031"),
            new JobTrigger("ReferenceDataReload", "CLOSEFIL2", "TCATBALF", "032"),
            new JobTrigger("ReferenceDataReload", "TRANCATG", "WAITSTEP", "031"),
            new JobTrigger("ReferenceDataReload", "WAITSTEP", "CLOSEFIL", "030"),
            new JobTrigger("ReferenceDataReload", "TCATBALF", "WAITSTEP", "032"),
            new JobTrigger("ReferenceDataReload", "WAITSTEP", "CLOSEFIL", "030"),
            new JobTrigger("FilePrints", "CLOSEFIL", "READACCT", "030"),
            new JobTrigger("FilePrints", "READACCT", "READCARD", "030"),
            new JobTrigger("FilePrints", "READCARD", "READCUST", "030"),
            new JobTrigger("FilePrints", "READCUST", "READXREF", "030"),
            new JobTrigger("FilePrints", "READXREF", "WAITSTEP", "030"),
            new JobTrigger("FilePrints", "WAITSTEP", "OPENFIL", "030"),
            new JobTrigger("Statements", "CLOSEFIL", "CREASTMT", "030"),
            new JobTrigger("Statements", "CREASTMT", "TXT2PDF1", "030"),
            new JobTrigger("Statements", "TXT2PDF1", "WAITSTEP", "030"),
            new JobTrigger("Statements", "WAITSTEP", "OPENFIL", "030"),
            new JobTrigger("CategoryBalanceReport", "OPENFIL", "CLOSEFIL", "031"),
            new JobTrigger("CategoryBalanceReport", "CLOSEFIL", "PRTCATBL", "031"),
            new JobTrigger("CategoryBalanceReport", "PRTCATBL", "WAITSTEP", "031"),
            new JobTrigger("CategoryBalanceReport", "WAITSTEP", "OPENFIL", "031"));

    private OperationsChainSchedule() {
    }

    /** Control-M triggers, in folder order (app/scheduler/CardDemo.controlm). */
    public static List<JobTrigger> controlM() {
        return CONTROL_M;
    }

    /** CA-7 triggers, in the order the LJOB listing reports them (app/scheduler/CardDemo.ca7). */
    public static List<JobTrigger> ca7() {
        return CA7;
    }
}
