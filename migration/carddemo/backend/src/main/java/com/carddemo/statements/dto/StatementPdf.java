package com.carddemo.statements.dto;

/** A rendered statement PDF and the download name {@code STMT-<acctId>-<cycle>.pdf}. */
public record StatementPdf(String filename, byte[] content) {
}
