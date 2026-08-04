package dev.schemtocreate.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Locates the folder Create reads schematics from.
 *
 * <p>Create's Schematic Table lists whatever sits in {@code .minecraft/schematics}, so
 * putting the output there directly removes the one manual step between converting a file
 * and seeing it in game.
 */
final class MinecraftPaths {

    private MinecraftPaths() {
    }

    /** The {@code schematics} folder of a default installation, if that installation exists. */
    static Optional<Path> schematicsFolder() {
        return minecraftFolder().map(root -> root.resolve("schematics"));
    }

    /** True when the folder is already there, so the UI can say so rather than promise it. */
    static boolean schematicsFolderExists() {
        return schematicsFolder().filter(Files::isDirectory).isPresent();
    }

    private static Optional<Path> minecraftFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path candidate;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                return Optional.empty();
            }
            candidate = Path.of(appData, ".minecraft");
        } else if (os.contains("mac")) {
            candidate = home().resolve("Library/Application Support/minecraft");
        } else {
            candidate = home().resolve(".minecraft");
        }
        return Files.isDirectory(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home", "."));
    }
}
