package com.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PdfConverterService {

    private static final Duration TIMEOUT =
            Duration.ofMinutes(2);

    private final Path soffice;

    public PdfConverterService() throws IOException {
        this.soffice = findSoffice();
    }

    private Path findSoffice() throws IOException {
        String programFiles =
                System.getenv("ProgramFiles");
        String programFilesX86 =
                System.getenv("ProgramFiles(x86)");

        List<Path> candidates = List.of(
                Path.of("C:\\Program Files\\LibreOffice"
                        + "\\program\\soffice.exe"),
                Path.of("C:\\Program Files (x86)\\LibreOffice"
                        + "\\program\\soffice.exe"),
                Path.of(System.getProperty("user.home"),
                        "scoop", "apps", "libreoffice",
                        "current", "program", "soffice.exe"),
                Path.of("C:\\Windows\\System32\\soffice.exe")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        for (String root : List.of(
                programFiles == null ? "" : programFiles,
                programFilesX86 == null ? "" : programFilesX86
        )) {
            if (!root.isBlank()) {
                Path candidate = Path.of(root, "LibreOffice",
                        "program", "soffice.exe");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        // Thử dùng soffice nếu người dùng đã cấu hình PATH.
        return Path.of("soffice.exe");
    }

    public Path convert(Path input, Path requestedOutputDir)
            throws IOException, InterruptedException {

        input = input.toAbsolutePath().normalize();

        if (!Files.isRegularFile(input)) {
            throw new IOException("File không tồn tại.");
        }

        String fileName = input.getFileName()
                .toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".doc")
                && !fileName.endsWith(".docx")) {
            throw new IOException(
                    "Chỉ hỗ trợ file .doc và .docx.");
        }

        Path outputDir = requestedOutputDir == null
                ? input.getParent()
                : requestedOutputDir.toAbsolutePath().normalize();

        Files.createDirectories(outputDir);

        String originalName = input.getFileName().toString();
        String baseName = originalName.replaceFirst(
                "(?i)\\.docx?$", "");

        Path output = uniqueOutput(outputDir, baseName);

        Path profile = Files.createTempDirectory(
                "wordtopdf-profile-");

        Path logFile = Files.createTempFile(
                "wordtopdf-", ".log");

        Process process = null;

        try {
            String profileUri = profile.toUri().toASCIIString();

            ProcessBuilder builder = new ProcessBuilder(
                    soffice.toString(),
                    "-env:UserInstallation=" + profileUri,
                    "--headless",
                    "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", outputDir.toString(),
                    input.toString()
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());

            process = builder.start();

            boolean completed = process.waitFor(
                    TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);

            if (!completed) {
                process.destroyForcibly();
                process.waitFor();

                throw new IOException(
                        "Quá thời gian chuyển đổi: "
                                + input.getFileName());
            }

            String log = Files.readString(logFile);

            if (process.exitValue() != 0
                    || !Files.isRegularFile(output)
                    || Files.size(output) == 0) {
                throw new IOException(
                        "LibreOffice không tạo được PDF. "
                                + log);
            }

            return output;

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }

            Files.deleteIfExists(logFile);
            deleteRecursively(profile);
        }
    }

    private Path uniqueOutput(Path dir, String baseName) {
        Path candidate = dir.resolve(baseName + ".pdf");

        int index = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(
                    baseName + " (" + index + ").pdf");
            index++;
        }

        return candidate;
    }

    private void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // File tạm có thể được hệ điều hành giữ.
                        }
                    });
        } catch (IOException ignored) {
            // Không làm mất kết quả chuyển đổi vì lỗi dọn dẹp.
        }
    }
}