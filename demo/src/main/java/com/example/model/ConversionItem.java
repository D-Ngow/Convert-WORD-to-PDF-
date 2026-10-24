package com.example.model;

import java.nio.file.Path;

public class ConversionItem {

    public enum Status {
        READY,
        CONVERTING,
        SUCCESS,
        FAILED
    }

    private final Path input;
    private Path output;
    private Status status = Status.READY;
    private String message = "Sẵn sàng";

    public ConversionItem(Path input) {
        this.input = input;
    }

    public Path getInput() {
        return input;
    }

    public Path getOutput() {
        return output;
    }

    public void setOutput(Path output) {
        this.output = output;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }
 
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return input.getFileName() + "  —  " + message;
    }
}