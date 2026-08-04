package dev.schemtocreate.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The "drop files here" area.
 *
 * <p>Also clickable, because drag and drop is not available to everyone: a file dialog is
 * the fallback for keyboard users and for anyone running the app maximised over the folder
 * they want to drag from.
 */
final class DropZone extends JPanel {

    private static final Color IDLE_BORDER = new Color(0x9A, 0xA3, 0xB2);
    private static final Color ACTIVE_BORDER = new Color(0x2D, 0x7D, 0xD2);
    private static final Color ACTIVE_FILL = new Color(0x2D, 0x7D, 0xD2, 0x1A);

    private final Consumer<List<Path>> onFilesDropped;
    private final Runnable onClick;
    private boolean hovering;

    DropZone(Consumer<List<Path>> onFilesDropped, Runnable onClick) {
        this.onFilesDropped = onFilesDropped;
        this.onClick = onClick;

        setOpaque(false);
        setLayout(new GridLayout(2, 1, 0, 4));
        setBorder(BorderFactory.createEmptyBorder(22, 16, 22, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        add(centeredLabel("Arraste seus arquivos .schem para cá", Font.BOLD, 15,
                new Color(0x33, 0x38, 0x40)));
        add(centeredLabel("ou clique para escolher — pastas também funcionam", Font.PLAIN, 12,
                new Color(0x6B, 0x72, 0x80)));

        new DropTarget(this, DnDConstants.ACTION_COPY, new FileDropListener(), true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                onClick.run();
            }
        });
    }

    private static JComponent centeredLabel(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(style, (float) size));
        label.setForeground(color);
        return label;
    }

    /** Installs the same drop behaviour on another component, e.g. the queue table. */
    void alsoAcceptDropsOn(JComponent component) {
        new DropTarget(component, DnDConstants.ACTION_COPY, new FileDropListener(), true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = 4;
            int width = getWidth() - inset * 2;
            int height = getHeight() - inset * 2;
            if (hovering) {
                g.setColor(ACTIVE_FILL);
                g.fillRoundRect(inset, inset, width, height, 14, 14);
            }
            g.setColor(hovering ? ACTIVE_BORDER : IDLE_BORDER);
            g.setStroke(new BasicStroke(hovering ? 2.2f : 1.4f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1f, new float[]{7f, 5f}, 0f));
            g.drawRoundRect(inset, inset, width, height, 14, 14);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }

    private void setHovering(boolean value) {
        if (hovering != value) {
            hovering = value;
            repaint();
        }
    }

    /** Accepts a drop only if it actually carries files, so the cursor tells the truth. */
    private final class FileDropListener implements DropTargetListener {

        @Override
        public void dragEnter(DropTargetDragEvent event) {
            if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.acceptDrag(DnDConstants.ACTION_COPY);
                setHovering(true);
            } else {
                event.rejectDrag();
            }
        }

        @Override
        public void dragOver(DropTargetDragEvent event) {
            // Nothing to do; dragEnter already decided.
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent event) {
            // Copy is the only action offered.
        }

        @Override
        public void dragExit(DropTargetEvent event) {
            setHovering(false);
        }

        @Override
        public void drop(DropTargetDropEvent event) {
            setHovering(false);
            if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.rejectDrop();
                return;
            }
            event.acceptDrop(DnDConstants.ACTION_COPY);
            try {
                Object data = event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = ((List<?>) data).stream()
                        .filter(File.class::isInstance)
                        .map(file -> ((File) file).toPath())
                        .toList();
                onFilesDropped.accept(paths);
                event.dropComplete(true);
            } catch (Exception e) {
                event.dropComplete(false);
            }
        }
    }
}
