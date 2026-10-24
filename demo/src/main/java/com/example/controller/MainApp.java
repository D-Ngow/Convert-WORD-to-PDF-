package com.example.controller;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.model.ConversionItem;
import com.example.service.OcrWordService;
import com.example.service.PdfConverterService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final ComboBox<String> modeBox = new ComboBox<>();

private final Button ocrButton =
        new Button("Chọn PDF/Ảnh → Word");

private final Label modeHint =
        new Label("Nhận dạng tiếng Việt và tiếng Anh");

private final List<String> supportedOcrExtensions =
        List.of(".pdf", ".png", ".jpg", ".jpeg",
                ".tif", ".tiff", ".bmp");

    private String removeExtension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
        return fileName;
    }

    int lastDot = fileName.lastIndexOf('.');

    // Không xóa dấu chấm đầu tiên của file ẩn.
    if (lastDot <= 0) {
        return fileName;
    }

    return fileName.substring(0, lastDot);
}

    private final ObservableList<ConversionItem> items =
            FXCollections.observableArrayList();

    private final ListView<ConversionItem> listView =
            new ListView<>(items);

    private final Label folderLabel =
            new Label("Mặc định: lưu cạnh file Word");

    private final Label statusLabel =
            new Label("Sẵn sàng");

    private final ProgressBar progressBar =
            new ProgressBar(0);

    private final Button addButton =
            new Button("+ Chọn file Word");

    private final Button removeButton =
            new Button("Xóa file chọn");

    private final Button clearButton =
            new Button("Xóa tất cả");

    private final Button folderButton =
            new Button("Chọn thư mục lưu");

    private final Button convertButton =
            new Button("Chuyển đổi sang PDF");

    private final Button openFolderButton =
            new Button("Mở thư mục kết quả");

    private Path outputDirectory;
    private Path lastOutputDirectory;
    private PdfConverterService converter;
    private final OcrWordService ocrService =
        new OcrWordService();
    private boolean converting = false; 

    private Path uniqueOutput(
        Path directory, String baseName, String extension) {

    Path candidate = directory.resolve(baseName + extension);

    int index = 2;

    while (Files.exists(candidate)) {
        candidate = directory.resolve(
                baseName + " (" + index + ")" + extension
        );
        index++;
    }

    return candidate;
}
    @Override
    public void start(Stage stage) {
       try {
    converter = new PdfConverterService();
} catch (IOException e) {
    showError(
            "Không thể khởi tạo dịch vụ chuyển đổi",
            e.getMessage()
    );
    return;
}

        Label title = new Label("Word to PDF");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label(
                "Chuyển đổi tài liệu Word ngay trên máy tính");
        subtitle.getStyleClass().add("subtitle");

        VBox heading = new VBox(6, title, subtitle);

        HBox toolbar = new HBox(
                10, addButton, removeButton, clearButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox dropZone = new VBox(8);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(22));
        dropZone.getStyleClass().add("drop-zone");

        Label dropIcon = new Label("↓");
        dropIcon.getStyleClass().add("drop-icon");

        Label dropTitle = new Label(
                "Kéo thả file Word vào đây");
        dropTitle.getStyleClass().add("drop-title");

        Label dropHint = new Label(
                "Hỗ trợ .doc và .docx · Có thể chọn nhiều file");
        dropHint.getStyleClass().add("subtitle");

        modeBox.getItems().addAll(
        "Word → PDF",
        "PDF/Ảnh → Word (OCR)"
);
modeBox.getSelectionModel().selectFirst();

modeBox.setOnAction(e -> {
    boolean ocrMode = modeBox.getSelectionModel()
            .getSelectedIndex() == 1;

    addButton.setText(
            ocrMode ? "+ Chọn PDF/Ảnh" : "+ Chọn file Word"
    );

    dropTitle.setText(
            ocrMode
                    ? "Kéo thả PDF hoặc ảnh vào đây"
                    : "Kéo thả file Word vào đây"
    );

    dropHint.setText(
            ocrMode
                    ? "Hỗ trợ PDF, PNG, JPG, TIFF, BMP"
                    : "Hỗ trợ .doc và .docx · Có thể chọn nhiều file"
    );

    convertButton.setText(
            ocrMode ? "Chuyển đổi sang Word" : "Chuyển đổi sang PDF"
    );
});

ocrButton.setOnAction(e -> {
    modeBox.getSelectionModel().select(1);
    chooseFiles(stage);
});
        dropZone.getChildren().addAll(
                dropIcon, dropTitle, dropHint);

        dropZone.setOnDragOver(event -> {
            Dragboard board = event.getDragboard();

            if (!converting && board.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY); 
            }

            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            boolean success = false;

            if (!converting && board.hasFiles()) {
                addFiles(board.getFiles().stream()
                        .map(file -> file.toPath()).toList());
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });

        listView.setPlaceholder(
                new Label("Chưa có file. Hãy chọn hoặc kéo thả file."));
        listView.setPrefHeight(260);

        listView.setCellFactory(view ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(
                            ConversionItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            setText(item.getInput().getFileName()
                                    + "\n" + item.getMessage());

                            setGraphic(null);
                        }
                    }
                });

        folderButton.setOnAction(e -> chooseOutputDirectory(stage));
        addButton.setOnAction(e -> chooseFiles(stage));
        removeButton.setOnAction(e -> removeSelected());
        clearButton.setOnAction(e -> clearFiles());
        convertButton.setOnAction(e -> startConversion());
        openFolderButton.setOnAction(e -> openResultFolder());

        folderLabel.setWrapText(true);

        HBox folderRow = new HBox(
                12, folderButton, folderLabel);
        folderRow.setAlignment(Pos.CENTER_LEFT);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMaxHeight(10);
        progressBar.setProgress(0);

        HBox actionRow = new HBox(
                12, convertButton, openFolderButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox footer = new VBox(
                12, folderRow, progressBar,
                statusLabel, actionRow);
        footer.setPadding(new Insets(16, 0, 0, 0));

        HBox modeRow = new HBox(
        10, new Label("Chế độ:"), modeBox, ocrButton
);
modeRow.setAlignment(Pos.CENTER_LEFT);

VBox root = new VBox(
        18, heading, modeRow, toolbar,
        dropZone, listView, footer
);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("root-pane");

        Scene scene = new Scene(root, 820, 700);

        var css = getClass().getResource("/styles/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Word to PDF");
        stage.setMinWidth(680);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    private void chooseFiles(Stage stage) {
    boolean ocrMode = modeBox.getSelectionModel()
            .getSelectedIndex() == 1;

    FileChooser chooser = new FileChooser();
    chooser.setTitle(
            ocrMode ? "Chọn PDF hoặc ảnh"
                    : "Chọn file Word"
    );

    if (ocrMode) {
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "PDF và ảnh",
                        "*.pdf", "*.png", "*.jpg", "*.jpeg",
                        "*.tif", "*.tiff", "*.bmp"
                )
        );
    } else {
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Tài liệu Word", "*.doc", "*.docx"
                )
        );
    }

    List<java.io.File> files =
            chooser.showOpenMultipleDialog(stage);

    if (files != null) {
        addFiles(files.stream()
                .map(java.io.File::toPath)
                .toList());
    }
}

    private void addFiles(List<Path> paths) {
    int added = 0;

    boolean ocrMode = modeBox.getSelectionModel()
            .getSelectedIndex() == 1;

    for (Path path : paths) {
        if (!Files.isRegularFile(path)) {
            continue;
        }

        String name = path.getFileName().toString()
                .toLowerCase(Locale.ROOT);

        boolean supported;

        if (ocrMode) {
            supported = supportedOcrExtensions.stream()
                    .anyMatch(name::endsWith);
        } else {
            supported = name.endsWith(".doc")
                    || name.endsWith(".docx");
        }

        if (!supported) {
            continue;
        }

        Path normalized = path.toAbsolutePath().normalize();

        boolean exists = items.stream()
                .anyMatch(item ->
                        item.getInput().toAbsolutePath()
                                .normalize().equals(normalized));

        if (!exists) {
            items.add(new ConversionItem(normalized));
            added++;
        }
    }

    statusLabel.setText("Đã thêm " + added + " file.");
}

    private void chooseOutputDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chọn thư mục lưu PDF");

        if (outputDirectory != null
                && Files.isDirectory(outputDirectory)) {
            chooser.setInitialDirectory(outputDirectory.toFile());
        }

        var selected = chooser.showDialog(stage);

        if (selected != null) {
            outputDirectory = selected.toPath();
            folderLabel.setText(outputDirectory.toString());
        }
    }

    private void removeSelected() {
        if (converting) {
            return;
        }

        ConversionItem selected =
                listView.getSelectionModel().getSelectedItem();

        if (selected != null) {
            items.remove(selected);
        }
    }

    private void clearFiles() {
        if (converting) {
            return;
        }

        items.clear();
        progressBar.setProgress(0);
        statusLabel.setText("Đã xóa danh sách.");
    }

    private void startConversion() {
    if (converting) {
        return;
    }

    if (items.isEmpty()) {
        showError(
                "Chưa có file",
                "Hãy chọn ít nhất một file phù hợp với chế độ chuyển đổi."
        );
        return;
    }

    // Xác định chế độ chuyển đổi một lần duy nhất.
    final boolean ocrMode =
            modeBox.getSelectionModel().getSelectedIndex() == 1;

    // Lưu lại thư mục đầu ra tại thời điểm bắt đầu.
    final Path selectedOutputDirectory = outputDirectory;

    // Sao chép danh sách để xử lý ổn định trong Task.
    List<ConversionItem> batch = new ArrayList<>(items);

    // Kiểm tra định dạng file theo chế độ.
    for (ConversionItem item : batch) {
        String name = item.getInput()
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        boolean valid = ocrMode
                ? supportedOcrExtensions.stream()
                        .anyMatch(name::endsWith)
                : name.endsWith(".doc")
                        || name.endsWith(".docx");

        if (!valid) {
            showError(
                    "Sai định dạng file",
                    "File không phù hợp với chế độ đang chọn: "
                            + item.getInput().getFileName()
            );
            return;
        }
    }

    // Bắt đầu xử lý.
    converting = true;
    setControlsDisabled(true);
    modeBox.setDisable(true);
    ocrButton.setDisable(true);

    progressBar.progressProperty().unbind();
    statusLabel.textProperty().unbind();

    progressBar.setProgress(0);
    statusLabel.setText("Đang chuẩn bị chuyển đổi...");

    Task<BatchResult> task = new Task<>() {
        @Override
        protected BatchResult call() {
            int success = 0;
            int failed = 0;
            int total = batch.size();

            for (int i = 0; i < total; i++) {
                if (isCancelled()) {
                    break;
                }

                ConversionItem item = batch.get(i);

                Platform.runLater(() -> {
                    item.setStatus(
                            ConversionItem.Status.CONVERTING);
                    item.setMessage("Đang chuyển đổi...");
                    listView.refresh();
                });

                updateMessage(
                        "Đang xử lý " + (i + 1) + "/" + total
                                + ": "
                                + item.getInput().getFileName()
                );

                try {
                    Path input = item.getInput()
                            .toAbsolutePath()
                            .normalize();

                    Path targetDirectory =
                            selectedOutputDirectory != null
                                    ? selectedOutputDirectory
                                            .toAbsolutePath()
                                            .normalize()
                                    : input.getParent();

                    Files.createDirectories(targetDirectory);

                    Path result;

                    if (ocrMode) {
                        // PDF/Ảnh -> Word bằng OCR.
                        String baseName = removeExtension(
                                input.getFileName().toString()
                        );

                        Path docx = uniqueOutput(
                                targetDirectory,
                                baseName,
                                ".docx"
                        );

                        result = ocrService.convert(input, docx);

                    } else {
                        // Word -> PDF bằng LibreOffice.
                        result = converter.convert(
                                input,
                                selectedOutputDirectory
                        );
                    }

                    final Path finalResult = result;

                    Platform.runLater(() -> {
                        item.setOutput(finalResult);
                        item.setStatus(
                                ConversionItem.Status.SUCCESS);
                        item.setMessage(
                                "Thành công: "
                                        + finalResult.getFileName()
                        );
                        listView.refresh();
                    });

                    lastOutputDirectory = result.getParent();
                    success++;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    Platform.runLater(() -> {
                        item.setStatus(
                                ConversionItem.Status.FAILED);
                        item.setMessage(
                                "Tác vụ bị gián đoạn");
                        listView.refresh();
                    });

                    failed++;
                    break;

                } catch (Exception e) {
                    String message = e.getMessage();

                    if (message == null || message.isBlank()) {
                        message = e.getClass().getSimpleName()
                                + ": lỗi không xác định";
                    }

                    final String errorMessage =
                            message.length() > 180
                                    ? message.substring(0, 180) + "..."
                                    : message;

                    Platform.runLater(() -> {
                        item.setStatus(
                                ConversionItem.Status.FAILED);
                        item.setMessage(
                                "Thất bại: " + errorMessage);
                        listView.refresh();
                    });

                    failed++;
                }

                updateProgress(i + 1, total);
            }

            return new BatchResult(success, failed);
        }
    };

    progressBar.progressProperty()
            .bind(task.progressProperty());
    statusLabel.textProperty()
            .bind(task.messageProperty());

    task.setOnSucceeded(event -> {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();

        BatchResult result = task.getValue();

        statusLabel.setText(
                "Hoàn tất: " + result.success()
                        + " thành công, "
                        + result.failed() + " thất bại."
        );

        finishConversion();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Kết quả chuyển đổi");
        alert.setHeaderText("Đã xử lý xong danh sách");
        alert.setContentText(
                "Thành công: " + result.success()
                        + "\nThất bại: " + result.failed()
        );
        alert.showAndWait();
    });

    task.setOnFailed(event -> {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();

        finishConversion();

        Throwable error = task.getException();

        showError(
                "Lỗi chuyển đổi",
                error == null
                        ? "Có lỗi không xác định."
                        : error.getMessage()
        );
    });

    task.setOnCancelled(event -> {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();

        finishConversion();
        statusLabel.setText("Đã hủy tác vụ.");
    });

    Thread worker = new Thread(
            task, "document-conversion-worker");
    worker.setDaemon(true);
    worker.start();
}


    private record BatchResult(int success, int failed) {}

    private void finishConversion() {
    converting = false;
    setControlsDisabled(false);
    modeBox.setDisable(false);
    ocrButton.setDisable(false);
}

    private void setControlsDisabled(boolean disabled) {
        addButton.setDisable(disabled);
        removeButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        folderButton.setDisable(disabled);
        convertButton.setDisable(disabled);
    }

    private void openResultFolder() {
        Path directory = outputDirectory != null
                ? outputDirectory
                : lastOutputDirectory;

        if (directory == null && !items.isEmpty()) {
            directory = items.get(0).getInput().getParent();
        }

        if (directory == null || !Files.isDirectory(directory)) {
            showError("Không tìm thấy thư mục",
                    "Hãy chuyển đổi một file hoặc chọn thư mục lưu.");
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            } else {
                new ProcessBuilder(
                        "explorer.exe", directory.toString()).start();
            }
        } catch (IOException e) {
            showError("Không thể mở thư mục", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null
                ? "Không có thông tin chi tiết."
                : message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}