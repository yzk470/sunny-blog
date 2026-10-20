package com.sunnyblog.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Slf4j
@Service
public class RagTextExtractor {

    public record Extracted(String text, String kind) {
    }

    public Extracted extract(Path path, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".pdf")) return new Extracted(pdf(path), "text");
            if (lower.endsWith(".docx")) return new Extracted(docx(path), "text");
            if (lower.endsWith(".pptx")) return new Extracted(pptx(path), "text");
            if (lower.endsWith(".xlsx")) return new Extracted(xlsx(path), "text");
            if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                return new Extracted(stripHtml(Files.readString(path, StandardCharsets.UTF_8)), "text");
            }
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp") || lower.endsWith(".gif")
                    || lower.endsWith(".bmp") || lower.endsWith(".tiff")) {
                return new Extracted("", "image");
            }
            return new Extracted(Files.readString(path, StandardCharsets.UTF_8), "text");
        } catch (Exception ex) {
            log.warn("RAG extract failed {}: {}", filename, ex.getMessage());
            throw new IllegalStateException("无法解析文件 " + filename + ": " + ex.getMessage(), ex);
        }
    }

    private String pdf(Path path) throws Exception {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String docx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row -> {
                row.getTableCells().forEach(cell -> sb.append(cell.getText()).append(" | "));
                sb.append('\n');
            }));
            return sb.toString();
        }
    }

    private String pptx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XMLSlideShow slides = new XMLSlideShow(in)) {
            StringBuilder sb = new StringBuilder();
            slides.getSlides().forEach(slide -> {
                sb.append("\n## Slide\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape text) {
                        sb.append(text.getText()).append('\n');
                    }
                }
            });
            return sb.toString();
        }
    }

    private String xlsx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            StringBuilder sb = new StringBuilder();
            workbook.forEach(sheet -> {
                sb.append("\n## ").append(sheet.getSheetName()).append('\n');
                sheet.forEach(row -> {
                    row.forEach(cell -> sb.append(cell.toString()).append(" | "));
                    sb.append('\n');
                });
            });
            return sb.toString();
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
