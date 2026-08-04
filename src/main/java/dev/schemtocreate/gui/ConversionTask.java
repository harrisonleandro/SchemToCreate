package dev.schemtocreate.gui;

import dev.schemtocreate.converter.ConversionOptions;
import dev.schemtocreate.converter.ConversionResult;
import dev.schemtocreate.converter.SchematicConverter;
import dev.schemtocreate.util.ProgressListener;

import javax.swing.SwingWorker;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Runs the queue off the event dispatch thread.
 *
 * <p>Converting on the EDT would freeze the window for the whole run — which for a large
 * schematic is tens of seconds of an apparently hung application. A {@link SwingWorker}
 * keeps the UI responsive and marshals every status update back to the EDT.
 */
final class ConversionTask extends SwingWorker<Void, ConversionTask.Update> {

    /** Where a converted file should be written. */
    @FunctionalInterface
    interface OutputResolver {
        Path outputFor(Path input);
    }

    /**
     * One published event.
     *
     * <p>Carries {@code finished} rather than letting the listener read the row's status:
     * {@link SwingWorker#publish} hands the listener the object as it looks when the event
     * dispatch thread gets round to it, so a row published at the start of a conversion and
     * again at the end would arrive twice already marked done — and be reported twice.
     */
    record Update(QueuedFile file, boolean finished) {
    }

    private final List<QueuedFile> queue;
    private final ConversionOptions options;
    private final OutputResolver outputResolver;
    private final BiConsumer<Update, Integer> onRowUpdated;
    private final Runnable onFinished;

    private volatile String currentLabel = "";

    ConversionTask(List<QueuedFile> queue,
                   ConversionOptions options,
                   OutputResolver outputResolver,
                   BiConsumer<Update, Integer> onRowUpdated,
                   Runnable onFinished) {
        this.queue = queue;
        this.options = options;
        this.outputResolver = outputResolver;
        this.onRowUpdated = onRowUpdated;
        this.onFinished = onFinished;
    }

    /** Name of the file being converted, for the status line. */
    String currentLabel() {
        return currentLabel;
    }

    @Override
    protected Void doInBackground() {
        SchematicConverter converter = new SchematicConverter(options);
        int done = 0;
        for (QueuedFile file : queue) {
            if (isCancelled()) {
                return null;
            }
            currentLabel = file.input().getFileName().toString();
            file.markConverting();
            publish(new Update(file, false));
            convertOne(converter, file);
            setProgress(Math.min(100, ++done * 100 / Math.max(1, queue.size())));
            publish(new Update(file, true));
        }
        return null;
    }

    private void convertOne(SchematicConverter converter, QueuedFile file) {
        try {
            ConversionResult result = converter.convert(
                    file.input(), outputResolver.outputFor(file.input()), ProgressListener.noop());
            file.markDone(result);
        } catch (SchematicConverter.FileExistsException e) {
            file.markSkipped("marque \"Substituir arquivos existentes\"");
        } catch (Exception e) {
            file.markFailed(describe(e));
        }
    }

    /** Exception messages are what the user sees, so fall back to the type when blank. */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @Override
    protected void process(List<Update> updates) {
        for (Update update : updates) {
            onRowUpdated.accept(update, getProgress());
        }
    }

    @Override
    protected void done() {
        onFinished.run();
    }
}
