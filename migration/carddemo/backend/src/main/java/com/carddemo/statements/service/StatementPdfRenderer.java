package com.carddemo.statements.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Target-side replacement for {@code %TXT2PDF} in app/jcl/TXT2PDF1.JCL (boundary B-13): lays the
 * archived text statement (STMTFILE X(80) records) out on fixed-pitch pages, one statement per
 * document and a page break every {@value #LINES_PER_PAGE} lines.
 */
@Component
public class StatementPdfRenderer {

    public static final int LINES_PER_PAGE = 60;

    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 12f;
    private static final float MARGIN_LEFT = 54f;
    private static final float MARGIN_TOP = 36f;

    public byte[] render(String textBody, String title) {
        List<String> lines = textBody == null ? List.of() : textBody.lines().toList();
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(title);
            info.setCreator("CardDemo S-21 OnlineStatements");
            int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                int from = p * LINES_PER_PAGE;
                int to = Math.min(lines.size(), from + LINES_PER_PAGE);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, FONT_SIZE);
                    content.setLeading(LEADING);
                    content.newLineAtOffset(MARGIN_LEFT,
                            page.getMediaBox().getHeight() - MARGIN_TOP - FONT_SIZE);
                    for (int i = from; i < to; i++) {
                        content.showText(encodable(font, lines.get(i)));
                        content.newLine();
                    }
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Courier is WinAnsi-encoded; anything it cannot draw (controls, non-Latin) becomes '?'. */
    private static String encodable(PDType1Font font, String line) {
        StringBuilder sb = new StringBuilder(line.length());
        line.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            boolean ok = !Character.isISOControl(cp);
            if (ok) {
                try {
                    font.encode(ch);
                } catch (IOException | IllegalArgumentException e) {
                    ok = false;
                }
            }
            sb.append(ok ? ch : "?");
        });
        return sb.toString();
    }
}
