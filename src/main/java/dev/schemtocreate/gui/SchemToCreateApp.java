package dev.schemtocreate.gui;

import dev.schemtocreate.util.Formats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The desktop window: drop schematics in, press convert, get {@code .nbt} files out.
 *
 * <p>Wraps the same converter the CLI uses; nothing about the conversion differs. The window
 * defaults its destination to {@code .minecraft/schematics} when it can find one, because
 * that is where a file has to end up before Create's Schematic Table will list it.
 */
public final class SchemToCreateApp extends JFrame {

    private static final Color HEADER_TEXT = new Color(0x1F, 0x24, 0x2B);
    private static final Color MUTED_TEXT = new Color(0x6B, 0x72, 0x80);
    private static final Color ERROR_TEXT = new Color(0xC0, 0x39, 0x2B);
    private static final Color SUCCESS_TEXT = new Color(0x1E, 0x7E, 0x34);

    private final FileQueueModel queue = new FileQueueModel();
    private final JTable table = new JTable(queue);
    private final OptionsPanel options = new OptionsPanel();
    private final JTextArea log = new JTextArea(6, 40);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel status = new JLabel("Nenhum arquivo na fila.");
    private final JButton convert = new JButton("Converter");
    private final JButton clear = new JButton("Limpar lista");
    private final JButton openOutput = new JButton("Abrir pasta");

    private ConversionTask running;
    private Path lastOutputFolder;

    public SchemToCreateApp() {
        super("SchemToCreate — WorldEdit (.schem) para Create (.nbt)");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(720, 560));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildCentre(), BorderLayout.CENTER);
        content.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(content);

        wireActions();
        refreshControls();
        pack();
        setSize(860, 640);
        setLocationRelativeTo(null);
    }

    private JComponent buildHeader() {
        JLabel title = new JLabel("SchemToCreate");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(HEADER_TEXT);

        JLabel subtitle = new JLabel(
                "Converte schematics do WorldEdit para o formato do Create 6.0.8 — "
                        + "Minecraft 1.20.1. Funciona sem internet.");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JComponent buildCentre() {
        DropZone dropZone = new DropZone(this::addFiles, this::chooseFiles);
        configureTable();
        dropZone.alsoAcceptDropsOn(table);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(700, 220));
        dropZone.alsoAcceptDropsOn((JComponent) tableScroll.getViewport().getView());

        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.setOpaque(false);
        centre.add(dropZone, BorderLayout.NORTH);
        centre.add(tableScroll, BorderLayout.CENTER);
        centre.add(buildLogPane(), BorderLayout.SOUTH);
        return centre;
    }

    private void configureTable() {
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new StatusRenderer());
        int[] widths = {230, 80, 240, 90, 200};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    private JComponent buildLogPane() {
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        log.setBackground(new Color(0xF7, 0xF8, 0xFA));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createTitledBorder("Detalhes e avisos"));
        scroll.setPreferredSize(new Dimension(700, 130));
        return scroll;
    }

    private JComponent buildFooter() {
        progress.setStringPainted(true);
        progress.setString("");
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
        status.setForeground(MUTED_TEXT);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(clear);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(openOutput);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(status);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(convert);

        convert.setFont(convert.getFont().deriveFont(Font.BOLD));
        getRootPane().setDefaultButton(convert);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(options);
        footer.add(Box.createVerticalStrut(10));
        footer.add(progress);
        footer.add(Box.createVerticalStrut(8));
        footer.add(buttons);
        return footer;
    }

    private void wireActions() {
        convert.addActionListener(event -> startConversion());
        clear.addActionListener(event -> {
            queue.clear();
            log.setText("");
            refreshControls();
        });
        openOutput.addActionListener(event -> openLastOutputFolder());
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Escolher schematics");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Schematics do WorldEdit (*.schem, *.schematic)", "schem", "schematic"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Path> selected = new ArrayList<>();
        for (var file : chooser.getSelectedFiles()) {
            selected.add(file.toPath());
        }
        addFiles(selected);
    }

    /**
     * Queues files without going through the drop zone, so the window can open with a
     * selection already in it — {@code --gui house.schem}, or an "open with" association.
     */
    public void enqueue(List<Path> paths) {
        if (!paths.isEmpty()) {
            addFiles(paths);
        }
    }

    private void addFiles(List<Path> paths) {
        int added = queue.add(paths);
        if (added == 0) {
            appendLog("Nenhum arquivo .schem encontrado no que foi solto.");
        }
        refreshControls();
    }

    private void startConversion() {
        var problem = options.validationProblem();
        if (problem.isPresent()) {
            JOptionPane.showMessageDialog(this, problem.get(), "Falta escolher a pasta",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        ConversionTask.OutputResolver resolver;
        try {
            resolver = options.outputResolver();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não consegui criar a pasta de saída:\n"
                    + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        queue.resetStatuses();
        log.setText("");
        progress.setValue(0);
        setBusy(true);

        List<QueuedFile> rows = queue.rows();
        running = new ConversionTask(rows, options.toConversionOptions(), resolver,
                this::onRowUpdated, this::onConversionFinished);
        lastOutputFolder = rows.isEmpty() ? null : resolver.outputFor(rows.get(0).input()).getParent();
        running.execute();
    }

    private void onRowUpdated(ConversionTask.Update update, int percent) {
        QueuedFile file = update.file();
        queue.rowChanged(file);
        progress.setValue(percent);
        progress.setString(percent + "%");
        if (!update.finished()) {
            status.setText("Convertendo " + file.input().getFileName() + "...");
            return;
        }
        reportRow(file);
    }

    private void reportRow(QueuedFile file) {
        String name = file.input().getFileName().toString();
        switch (file.status()) {
            case DONE -> {
                appendLog(name + "  ->  " + file.result().output().getFileName()
                        + "   " + file.result().region()
                        + "   " + Formats.count(file.result().write().blocksWritten()) + " blocos"
                        + "   " + Formats.bytes(file.result().write().compressedBytes()));
                file.warnings().forEach(warning -> appendLog("    aviso: " + warning));
                // A version gap makes unknown blocks vanish into air with nothing logged, so
                // the block list is shown unprompted: it is the only way to see which ones.
                if (!file.warnings().isEmpty()) {
                    appendPaletteListing(file);
                }
            }
            case FAILED -> appendLog(name + "  ->  ERRO: " + file.message());
            case SKIPPED -> appendLog(name + "  ->  pulado, o .nbt já existe ("
                    + file.message() + ")");
            default -> {
                // Queued and converting rows are reflected in the table, not the log.
            }
        }
    }

    private void onConversionFinished() {
        setBusy(false);
        running = null;
        long done = queue.rows().stream().filter(f -> f.status() == QueuedFile.Status.DONE).count();
        long failed = queue.rows().stream().filter(f -> f.status() == QueuedFile.Status.FAILED).count();
        long skipped = queue.rows().stream().filter(f -> f.status() == QueuedFile.Status.SKIPPED).count();

        status.setForeground(failed > 0 ? ERROR_TEXT : SUCCESS_TEXT);
        status.setText(summarise(done, failed, skipped));
        progress.setValue(100);
        progress.setString(failed > 0 ? "concluído com erros" : "concluído");
        appendLog("");
        appendLog(summarise(done, failed, skipped));
        // The destination folder may have just been created, so its caption is now stale.
        options.refreshDestinationCaption();
        refreshControls();
    }

    private static String summarise(long done, long failed, long skipped) {
        StringBuilder text = new StringBuilder(done + " convertido(s)");
        if (skipped > 0) {
            text.append(", ").append(skipped).append(" pulado(s)");
        }
        if (failed > 0) {
            text.append(", ").append(failed).append(" com erro");
        }
        return text.toString();
    }

    private void setBusy(boolean busy) {
        convert.setEnabled(!busy);
        clear.setEnabled(!busy);
        options.setControlsEnabled(!busy);
        progress.setIndeterminate(false);
        if (busy) {
            status.setForeground(MUTED_TEXT);
            status.setText("Convertendo...");
        }
    }

    private void refreshControls() {
        boolean hasFiles = !queue.isEmpty();
        convert.setEnabled(hasFiles && running == null);
        clear.setEnabled(hasFiles && running == null);
        openOutput.setEnabled(lastOutputFolder != null && Desktop.isDesktopSupported());
        if (running == null && !hasFiles) {
            status.setForeground(MUTED_TEXT);
            status.setText("Nenhum arquivo na fila.");
            progress.setValue(0);
            progress.setString("");
        } else if (running == null) {
            int pending = (int) queue.rows().stream().filter(QueuedFile::isPending).count();
            if (pending == queue.rows().size()) {
                status.setForeground(MUTED_TEXT);
                status.setText(pending + " arquivo(s) na fila.");
            }
        }
    }

    private void openLastOutputFolder() {
        if (lastOutputFolder == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(lastOutputFolder.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            appendLog("Não consegui abrir a pasta: " + lastOutputFolder);
        }
    }

    private void appendPaletteListing(QueuedFile file) {
        var usage = file.result().write().blockUsage();
        appendLog("    blocos usados (" + usage.size() + " tipos, mais comuns primeiro) —"
                + " confira quais não existem na sua versão:");
        for (var entry : usage) {
            appendLog(String.format("      %9s x %s", Formats.count(entry.count()), entry.name()));
        }
    }

    private void appendLog(String line) {
        log.append(line + System.lineSeparator());
        log.setCaretPosition(log.getDocument().getLength());
    }

    /** Colours the status column so failures stand out without reading the text. */
    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    source, value, selected, focused, row, column);
            if (!selected) {
                component.setForeground(colourFor(queue.row(row).status(), column));
            }
            return component;
        }

        private Color colourFor(QueuedFile.Status rowStatus, int column) {
            if (column != 2) {
                return HEADER_TEXT;
            }
            return switch (rowStatus) {
                case FAILED -> ERROR_TEXT;
                case DONE -> SUCCESS_TEXT;
                case SKIPPED -> new Color(0xB7, 0x7A, 0x0B);
                default -> MUTED_TEXT;
            };
        }
    }

    /** Shows the window on the event dispatch thread, optionally pre-loaded with files. */
    public static void launch(List<Path> initialFiles) {
        SwingUtilities.invokeLater(() -> {
            SchemToCreateApp app = new SchemToCreateApp();
            app.enqueue(initialFiles);
            app.setVisible(true);
        });
    }
}
