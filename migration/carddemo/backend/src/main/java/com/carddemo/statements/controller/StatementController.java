package com.carddemo.statements.controller;

import com.carddemo.statements.dto.StatementDetailResponse;
import com.carddemo.statements.dto.StatementPageResponse;
import com.carddemo.statements.dto.StatementPdf;
import com.carddemo.statements.service.StatementService;
import com.carddemo.statements.service.StatementSignOnGuard;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** S-21 OnlineStatements: list an account's archived statements, read one, serve its HTML or PDF. */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    /**
     * The archived HTML is CREASTMT output (inline styles only). No script, plugin, form or
     * remote resource may run in it; it may only be framed by the CardDemo origin.
     * {@code frame-ancestors} supersedes the filter chain's {@code X-Frame-Options: DENY} in
     * browsers, so the statement view can embed it.
     */
    static final String HTML_CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'none'; "
            + "object-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'self'";

    private static final MediaType TEXT_HTML_UTF8 =
            new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final StatementService service;
    private final StatementSignOnGuard signOnGuard;

    public StatementController(StatementService service, StatementSignOnGuard signOnGuard) {
        this.service = service;
        this.signOnGuard = signOnGuard;
    }

    @GetMapping
    public StatementPageResponse list(@RequestParam(value = "acctId", required = false) String acctId,
                                      @RequestParam(value = "page", required = false) String page) {
        signOnGuard.requireSignedOn();
        return service.list(acctId, page);
    }

    @GetMapping("/{stmtId}")
    public StatementDetailResponse detail(@PathVariable("stmtId") String stmtId) {
        signOnGuard.requireSignedOn();
        return service.detail(stmtId);
    }

    @GetMapping("/{stmtId}/html")
    public ResponseEntity<String> html(@PathVariable("stmtId") String stmtId) {
        signOnGuard.requireSignedOn();
        String body = service.html(stmtId);
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .header("Content-Security-Policy", HTML_CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }

    /** TXT2PDF1.JCL's PDF (boundary B-13), rendered from the archived text statement on request. */
    @GetMapping("/{stmtId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable("stmtId") String stmtId) {
        signOnGuard.requireSignedOn();
        StatementPdf pdf = service.pdf(stmtId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf.content());
    }
}
