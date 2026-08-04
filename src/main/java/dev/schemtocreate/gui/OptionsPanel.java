package dev.schemtocreate.gui;

import dev.schemtocreate.converter.ConversionOptions;
import dev.schemtocreate.converter.SchematicConverter;
import dev.schemtocreate.writer.CreateWriterOptions;
import dev.schemtocreate.writer.StructureVoidPolicy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Destination and conversion settings. */
final class OptionsPanel extends JPanel {

    private enum Destination {
        BESIDE_INPUT("Junto do arquivo original"),
        MINECRAFT("Pasta schematics do Minecraft"),
        CUSTOM("Outra pasta...");

        private final String label;

        Destination(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final JComboBox<Destination> destination = new JComboBox<>(Destination.values());
    private final JLabel destinationPath = new JLabel();
    private final JButton browse = new JButton("Procurar...");
    private final JCheckBox includeEntities = new JCheckBox("Incluir entidades", true);
    private final JCheckBox overwrite = new JCheckBox("Substituir arquivos existentes", true);
    private final JCheckBox keepStructureVoid = new JCheckBox("Manter structure_void", false);
    private final JTextField replacements = new JTextField();

    private Path customFolder;

    OptionsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        setOpaque(false);

        // Every child needs the same alignmentX: a BoxLayout aligns siblings to a shared
        // axis, so one component left at the JPanel default of CENTER shifts the rest.
        JComponent destinationRow = buildDestinationRow();
        JComponent checkboxRow = buildCheckboxRow();
        destinationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkboxRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent replacementRow = buildReplacementRow();
        replacementRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(destinationRow);
        add(Box.createVerticalStrut(8));
        add(replacementRow);
        add(Box.createVerticalStrut(8));
        add(checkboxRow);

        destination.addActionListener(event -> refreshDestinationLabel());
        browse.addActionListener(event -> chooseFolder());
        configureTooltips();
        selectSensibleDefault();
    }

    private JComponent buildDestinationRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel caption = new JLabel("Salvar em:");
        caption.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(caption, BorderLayout.WEST);
        left.add(destination, BorderLayout.CENTER);

        destinationPath.setFont(destinationPath.getFont().deriveFont(Font.PLAIN, 11f));
        destinationPath.setForeground(new Color(0x6B, 0x72, 0x80));
        destinationPath.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

        JPanel stacked = new JPanel(new BorderLayout());
        stacked.setOpaque(false);
        stacked.add(left, BorderLayout.CENTER);
        stacked.add(destinationPath, BorderLayout.SOUTH);

        row.add(stacked, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    /**
     * Free-text block substitutions, mirroring the command line's {@code --replace}.
     *
     * <p>A text field rather than a picker because the blocks worth replacing are only known
     * after a first conversion prints the block list — the workflow is convert, read the
     * list, paste the swaps in, convert again.
     */
    private JComponent buildReplacementRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JLabel caption = new JLabel("Substituir blocos:");
        caption.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        replacements.setToolTipText("<html>Para builds feitas em versões mais novas que a sua.<br>"
                + "Separe por vírgula: <b>de=para, de=para</b><br>"
                + "Exemplo: <b>pale_oak_planks=spruce_planks, stripped_pale_oak_log=stripped_spruce_log</b>"
                + "<br>O prefixo minecraft: é opcional. As propriedades do bloco são mantidas.</html>");

        JLabel hint = new JLabel("opcional — ex.: pale_oak_planks=spruce_planks, "
                + "stripped_pale_oak_log=stripped_spruce_log");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(new Color(0x6B, 0x72, 0x80));
        hint.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

        JPanel field = new JPanel(new BorderLayout());
        field.setOpaque(false);
        field.add(replacements, BorderLayout.CENTER);
        field.add(hint, BorderLayout.SOUTH);

        row.add(caption, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /**
     * Parses the {@code from=to, from=to} field. Malformed fragments are skipped rather than
     * rejected: a half-typed entry should not block a conversion the rest of which is fine.
     */
    private Map<String, String> parseReplacements() {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : replacements.getText().split(",")) {
            int equals = pair.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String from = withNamespace(pair.substring(0, equals));
            String to = withNamespace(pair.substring(equals + 1));
            if (!from.endsWith(":") && !to.endsWith(":")) {
                parsed.put(from, to);
            }
        }
        return parsed;
    }

    private static String withNamespace(String name) {
        String trimmed = name.trim();
        return trimmed.indexOf(':') < 0 ? "minecraft:" + trimmed : trimmed;
    }

    private JComponent buildCheckboxRow() {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JCheckBox box : new JCheckBox[]{includeEntities, overwrite, keepStructureVoid}) {
            box.setOpaque(false);
            row.add(box);
            row.add(Box.createHorizontalStrut(14));
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private void configureTooltips() {
        includeEntities.setToolTipText("<html>O Schematicannon imprime entidades numa etapa final "
                + "e consome um item para cada uma.<br>Desmarque se não quiser fornecer esses materiais."
                + "</html>");
        overwrite.setToolTipText("Sem isto, um arquivo .nbt já existente é preservado e a conversão é pulada.");
        keepStructureVoid.setToolTipText("<html>Por padrão structure_void vira ar, que é o que o próprio "
                + "Create grava<br>ao salvar um schematic.</html>");
    }

    /** Defaults to the Minecraft folder when there is one, since that is the end destination. */
    private void selectSensibleDefault() {
        boolean available = MinecraftPaths.schematicsFolder().isPresent();
        destination.setSelectedItem(available ? Destination.MINECRAFT : Destination.BESIDE_INPUT);
        refreshDestinationLabel();
    }

    /** Re-renders the caption under the destination picker, e.g. after a folder was created. */
    void refreshDestinationCaption() {
        refreshDestinationLabel();
    }

    private void refreshDestinationLabel() {
        Destination selected = (Destination) destination.getSelectedItem();
        browse.setEnabled(selected == Destination.CUSTOM);
        destinationPath.setText(describeDestination(selected));
    }

    private String describeDestination(Destination selected) {
        if (selected == Destination.BESIDE_INPUT) {
            return "Cada .nbt fica na mesma pasta do .schem correspondente.";
        }
        if (selected == Destination.CUSTOM) {
            return customFolder == null ? "Nenhuma pasta escolhida ainda." : customFolder.toString();
        }
        Optional<Path> folder = MinecraftPaths.schematicsFolder();
        if (folder.isEmpty()) {
            return "Instalação do Minecraft não encontrada — escolha outra pasta.";
        }
        return folder.get() + (MinecraftPaths.schematicsFolderExists()
                ? "" : "  (será criada)");
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolher pasta de saída");
        if (customFolder != null) {
            chooser.setCurrentDirectory(customFolder.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            customFolder = chooser.getSelectedFile().toPath();
            refreshDestinationLabel();
        }
    }

    /**
     * @return an unresolved destination the user still has to fix, or empty when ready
     */
    Optional<String> validationProblem() {
        Destination selected = (Destination) destination.getSelectedItem();
        if (selected == Destination.CUSTOM && customFolder == null) {
            return Optional.of("Escolha a pasta de saída antes de converter.");
        }
        if (selected == Destination.MINECRAFT && MinecraftPaths.schematicsFolder().isEmpty()) {
            return Optional.of("Não encontrei a pasta do Minecraft. Escolha outra pasta de saída.");
        }
        return Optional.empty();
    }

    /** Creates the destination folder if needed and returns the resolver for the run. */
    ConversionTask.OutputResolver outputResolver() throws IOException {
        Destination selected = (Destination) destination.getSelectedItem();
        if (selected == Destination.BESIDE_INPUT) {
            return SchematicConverter::defaultOutputFor;
        }
        Path folder = selected == Destination.CUSTOM
                ? customFolder
                : MinecraftPaths.schematicsFolder().orElseThrow();
        Files.createDirectories(folder);
        return input -> folder.resolve(SchematicConverter.defaultOutputFor(input).getFileName());
    }

    ConversionOptions toConversionOptions() {
        CreateWriterOptions writerOptions = CreateWriterOptions.defaults()
                .withIncludeEntities(includeEntities.isSelected())
                .withStructureVoid(keepStructureVoid.isSelected()
                        ? StructureVoidPolicy.KEEP
                        : StructureVoidPolicy.AIR)
                .withBlockReplacements(parseReplacements());
        return ConversionOptions.defaults()
                .withWriter(writerOptions)
                .withOverwrite(overwrite.isSelected());
    }

    /** Sets the substitution field's contents. Used by tests. */
    void setReplacementText(String text) {
        replacements.setText(text);
    }

    void setControlsEnabled(boolean enabled) {
        destination.setEnabled(enabled);
        browse.setEnabled(enabled && destination.getSelectedItem() == Destination.CUSTOM);
        includeEntities.setEnabled(enabled);
        overwrite.setEnabled(enabled);
        keepStructureVoid.setEnabled(enabled);
        replacements.setEnabled(enabled);
    }
}
