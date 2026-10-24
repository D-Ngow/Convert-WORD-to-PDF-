
package com.example.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import net.sourceforge.tess4j.Tesseract;

public class OcrWordService {

    private final Path tessdataPath;

    public OcrWordService() {
        // Thư mục tessdata nằm ở thư mục gốc dự án.
        this.tessdataPath = Path.of("tessdata")
                .toAbsolutePath()
                .normalize();
    }

    

private BufferedImage preprocessImage(BufferedImage source) {
    int width = source.getWidth() * 3 / 2;
    int height = source.getHeight() * 3 / 2;

    BufferedImage enlarged = new BufferedImage(
            width, height, BufferedImage.TYPE_BYTE_GRAY);

    Graphics2D g = enlarged.createGraphics();
    g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);

    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();

    return enlarged;
}





private String recognizeText(Tesseract ocr, BufferedImage image)
        throws Exception {

    BufferedImage processed = preprocessImage(image);

    try {
        String text = ocr.doOCR(processed);
        return fixBulletSymbols(text);
    } finally {
        processed.flush();
    }
}



    public Path convert(Path input, Path output)
            throws Exception {

        if (input == null || !Files.isRegularFile(input)) {
            throw new IOException(
                    "File đầu vào không tồn tại.");
        }

        if (output == null) {
            throw new IOException(
                    "Chưa xác định đường dẫn file đầu ra.");
        }

        input = input.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();

        if (input.equals(output)) {
            throw new IOException(
                    "File đầu ra phải khác file đầu vào.");
        }

        // Kiểm tra thư mục dữ liệu OCR.
        if (!Files.isDirectory(tessdataPath)) {
            throw new IOException(
                    "Không tìm thấy thư mục tessdata: "
                            + tessdataPath);
        }

        Path vieData = tessdataPath.resolve("vie.traineddata");
        Path engData = tessdataPath.resolve("eng.traineddata");

        if (!Files.isRegularFile(vieData)
                || !Files.isRegularFile(engData)) {
            throw new IOException(
                    "Thiếu vie.traineddata hoặc "
                            + "eng.traineddata trong thư mục: "
                            + tessdataPath);
        }

        String name = input.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        // Khởi tạo OCR cho mỗi lần chuyển đổi.
        Tesseract ocr = new Tesseract();
        ocr.setDatapath(tessdataPath.toString());
        ocr.setLanguage("vie+eng");
        ocr.setPageSegMode(6);

        Path parent = output.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (XWPFDocument word = new XWPFDocument()) {

            if (name.endsWith(".pdf")) {

                convertPdf(input, ocr, word);

            } else if (
                    name.endsWith(".png")
                    || name.endsWith(".jpg")
                    || name.endsWith(".jpeg")
                    || name.endsWith(".tif")
                    || name.endsWith(".tiff")
                    || name.endsWith(".bmp")) {

                BufferedImage image =
                        ImageIO.read(input.toFile());

                if (image == null) {
                    throw new IOException(
                            "Không đọc được ảnh đầu vào.");
                }

               String text = recognizeText(ocr, image);

                addText(word, text);

            } else {
                throw new IOException(
                        "Chỉ hỗ trợ PDF, PNG, JPG, JPEG, "
                                + "TIFF và BMP.");
            }

            try (OutputStream out =
                         Files.newOutputStream(output)) {
                word.write(out);
            }
        }

        if (!Files.isRegularFile(output)
                || Files.size(output) == 0) {
            throw new IOException(
                    "Không tạo được file Word đầu ra.");
        }

        return output;
    }

    private void convertPdf(
            Path input,
            Tesseract ocr,
            XWPFDocument word) throws Exception {

        try (PDDocument pdf =
                     org.apache.pdfbox.Loader.loadPDF(
                             input.toFile())) {

            if (pdf.getNumberOfPages() == 0) {
                throw new IOException(
                        "PDF không có trang nào.");
            }

            PDFRenderer renderer = new PDFRenderer(pdf);

            for (int i = 0;
                 i < pdf.getNumberOfPages();
                 i++) {

                // Render từng trang ở 300 DPI để OCR.
                BufferedImage page =
                        renderer.renderImageWithDPI(
                                i, 450, ImageType.GRAY);

                try {
                    String text = recognizeText(ocr, page);

                    if (i > 0) {
                        word.createParagraph()
                                .setPageBreak(true);
                    }

                    addText(word, text);

                } finally {
                    page.flush();
                }
            }
        }
    }

    private String fixBulletSymbols(String text) {
    if (text == null || text.isEmpty()) {
        return text;
    }

    String[] lines = text.split("\\R", -1);

    for (int i = 0; i < lines.length; i++) {
        // Chỉ đổi e/o đứng riêng ở đầu dòng,
        // theo sau là khoảng trắng.
        lines[i] = lines[i].replaceFirst(
                "^(\\s*)[eo](?=\\s+)",
                "$1\u2022"
        );
    }

    return String.join("\n", lines);
}

private void addText(
            XWPFDocument word,
            String text) {

        if (text == null || text.isBlank()) {
            word.createParagraph()
                    .createRun()
                    .setText("[Không nhận dạng được văn bản]");
            return;
        }

        String[] lines = text.split("\\R", -1);

        for (String line : lines) {
            XWPFParagraph paragraph =
                    word.createParagraph();

            XWPFRun run = paragraph.createRun();

            run.setFontFamily("Arial");
            run.setFontSize(11);
            run.setText(line.isEmpty() ? " " : line);
        }
    }
}
