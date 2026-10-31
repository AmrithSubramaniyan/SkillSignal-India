package in.skillsignal.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.Locale;

@Component
public class ResumeTextExtractor {

    public static final long MAX_BYTES = 5L * 1024L * 1024L;

    public String validateAndExtract(String originalFilename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the 5 MB limit.");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required.");
        }

        String lowerName = originalFilename.trim().toLowerCase(Locale.ROOT);
        boolean isPdfExt = lowerName.endsWith(".pdf");
        boolean isDocxExt = lowerName.endsWith(".docx");

        if (!isPdfExt && !isDocxExt) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file extension. Only .pdf and .docx files are allowed."
            );
        }

        if (isPdfExt && !hasPdfMagicBytes(bytes)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content does not match PDF magic bytes (%PDF)."
            );
        }
        if (isDocxExt && !hasZipMagicBytes(bytes)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content does not match DOCX container magic bytes."
            );
        }

        String extracted;
        try {
            if (isPdfExt) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    extracted = stripper.getText(doc);
                }
            } else {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    extracted = extractor.getText();
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Corrupted or unreadable document file."
            );
        }

        String cleaned = extracted != null ? extracted.trim() : "";
        if (cleaned.replaceAll("\\s+", "").length() < 10) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No readable text found in file. Scanned image files without a text layer are not supported (OCR is not available)."
            );
        }
        if (cleaned.length() > 15_000) {
            cleaned = cleaned.substring(0, 15_000);
        }
        return cleaned;
    }

    private boolean hasPdfMagicBytes(byte[] b) {
        // %PDF -> 0x25 0x50 0x44 0x46
        return b.length >= 4
                && b[0] == 0x25
                && b[1] == 0x50
                && b[2] == 0x44
                && b[3] == 0x46;
    }

    private boolean hasZipMagicBytes(byte[] b) {
        // PK\x03\x04 -> 0x50 0x4B 0x03 0x04
        return b.length >= 4
                && b[0] == 0x50
                && b[1] == 0x4B
                && b[2] == 0x03
                && b[3] == 0x04;
    }
}

