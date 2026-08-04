package dev.schemtocreate.gui;

import dev.schemtocreate.converter.ConversionResult;

import java.nio.file.Path;
import java.util.List;

/** One row of the conversion queue: an input file and what has happened to it so far. */
final class QueuedFile {

    enum Status {
        QUEUED("Na fila"),
        CONVERTING("Convertendo..."),
        DONE("Convertido"),
        SKIPPED("Já existe"),
        FAILED("Erro");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private final Path input;
    private final long sizeBytes;
    private Status status = Status.QUEUED;
    private ConversionResult result;
    private String message = "";

    QueuedFile(Path input, long sizeBytes) {
        this.input = input;
        this.sizeBytes = sizeBytes;
    }

    Path input() {
        return input;
    }

    long sizeBytes() {
        return sizeBytes;
    }

    Status status() {
        return status;
    }

    ConversionResult result() {
        return result;
    }

    /** Error text, or the reason a file was skipped. Empty when nothing went wrong. */
    String message() {
        return message;
    }

    /** Create compatibility notes for a converted file; empty otherwise. */
    List<String> warnings() {
        return result == null ? List.of() : result.warnings();
    }

    void markConverting() {
        status = Status.CONVERTING;
        message = "";
    }

    void markDone(ConversionResult conversionResult) {
        status = Status.DONE;
        result = conversionResult;
        message = conversionResult.warnings().isEmpty()
                ? ""
                : conversionResult.warnings().size() + " aviso(s)";
    }

    void markSkipped(String reason) {
        status = Status.SKIPPED;
        message = reason;
    }

    void markFailed(String reason) {
        status = Status.FAILED;
        message = reason;
    }

    boolean isPending() {
        return status == Status.QUEUED;
    }
}
