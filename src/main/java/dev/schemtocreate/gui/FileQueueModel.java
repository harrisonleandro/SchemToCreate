package dev.schemtocreate.gui;

import dev.schemtocreate.converter.ConversionResult;
import dev.schemtocreate.util.Formats;

import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Backing model for the queue table. Lives on the event dispatch thread. */
final class FileQueueModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Arquivo", "Tamanho", "Situação", "Blocos", "Saída"};
    private static final Set<String> ACCEPTED_EXTENSIONS = Set.of("schem", "schematic");

    private final List<QueuedFile> rows = new ArrayList<>();
    private final Set<Path> seen = new LinkedHashSet<>();

    /**
     * Adds files, expanding any dropped directory. Duplicates are ignored so dropping the
     * same folder twice does not queue everything twice.
     *
     * @return how many rows were actually added
     */
    int add(List<Path> paths) {
        int added = 0;
        for (Path path : paths) {
            added += addOne(path);
        }
        if (added > 0) {
            fireTableDataChanged();
        }
        return added;
    }

    private int addOne(Path path) {
        if (Files.isDirectory(path)) {
            return addFromDirectory(path);
        }
        if (!isSchematic(path) || !seen.add(path.toAbsolutePath())) {
            return 0;
        }
        rows.add(new QueuedFile(path, sizeOf(path)));
        return 1;
    }

    private int addFromDirectory(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> found = walk.filter(Files::isRegularFile)
                    .filter(FileQueueModel::isSchematic)
                    .sorted()
                    .toList();
            int added = 0;
            for (Path file : found) {
                if (seen.add(file.toAbsolutePath())) {
                    rows.add(new QueuedFile(file, sizeOf(file)));
                    added++;
                }
            }
            return added;
        } catch (IOException e) {
            return 0;
        }
    }

    static boolean isSchematic(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && ACCEPTED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    void clear() {
        rows.clear();
        seen.clear();
        fireTableDataChanged();
    }

    /** Drops rows that converted successfully, keeping failures for another attempt. */
    void removeCompleted() {
        rows.removeIf(row -> row.status() == QueuedFile.Status.DONE);
        seen.clear();
        rows.forEach(row -> seen.add(row.input().toAbsolutePath()));
        fireTableDataChanged();
    }

    List<QueuedFile> rows() {
        return List.copyOf(rows);
    }

    QueuedFile row(int index) {
        return rows.get(index);
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Resets every row to queued, so a second run reconverts everything. */
    void resetStatuses() {
        for (int i = 0; i < rows.size(); i++) {
            QueuedFile previous = rows.get(i);
            rows.set(i, new QueuedFile(previous.input(), previous.sizeBytes()));
        }
        fireTableDataChanged();
    }

    void rowChanged(QueuedFile file) {
        int index = rows.indexOf(file);
        if (index >= 0) {
            fireTableRowsUpdated(index, index);
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        QueuedFile file = rows.get(rowIndex);
        ConversionResult result = file.result();
        return switch (columnIndex) {
            case 0 -> file.input().getFileName().toString();
            case 1 -> Formats.bytes(file.sizeBytes());
            case 2 -> describeStatus(file);
            case 3 -> result == null ? "" : Formats.count(result.write().blocksWritten());
            case 4 -> result == null ? "" : result.output().getFileName().toString();
            default -> "";
        };
    }

    private static String describeStatus(QueuedFile file) {
        return file.message().isEmpty()
                ? file.status().label()
                : file.status().label() + " — " + file.message();
    }
}
