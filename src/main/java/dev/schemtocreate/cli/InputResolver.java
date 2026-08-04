package dev.schemtocreate.cli;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Expands command line inputs into a concrete file list.
 *
 * <p>Globs are expanded here rather than left to the shell because {@code cmd.exe} and
 * PowerShell do not expand them, so {@code schemtocreate *.schem} would otherwise work on
 * Linux and fail on Windows.
 */
final class InputResolver {

    private static final Set<String> SCHEMATIC_EXTENSIONS = Set.of("schem", "schematic");

    private final boolean recursive;

    InputResolver(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * @param inputs raw arguments: files, directories or glob patterns
     * @return deduplicated, sorted files
     * @throws IOException if an argument matches nothing
     */
    List<Path> resolve(List<String> inputs) throws IOException {
        Set<Path> resolved = new LinkedHashSet<>();
        for (String input : inputs) {
            List<Path> matches = resolveOne(input);
            if (matches.isEmpty()) {
                throw new IOException("No schematic files matched: " + input);
            }
            resolved.addAll(matches);
        }
        List<Path> sorted = new ArrayList<>(resolved);
        sorted.sort(Comparator.comparing(Path::toString));
        return sorted;
    }

    private List<Path> resolveOne(String input) throws IOException {
        if (isGlob(input)) {
            return expandGlob(input);
        }
        Path path = Path.of(input);
        if (Files.isDirectory(path)) {
            return collectFromDirectory(path);
        }
        return Files.exists(path) ? List.of(path) : List.of();
    }

    private static boolean isGlob(String input) {
        return input.indexOf('*') >= 0 || input.indexOf('?') >= 0;
    }

    /**
     * Splits the pattern as text before touching {@link Path}. On Windows {@code Path.of}
     * throws {@code InvalidPathException} for any string containing {@code *}, so the
     * directory and the name pattern have to be separated first.
     */
    private List<Path> expandGlob(String pattern) throws IOException {
        String normalized = pattern.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String directoryPart = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        String namePattern = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        if (isGlob(directoryPart)) {
            throw new IOException(
                    "Wildcards are only supported in the file name: " + pattern
                            + " (use --recursive to descend into directories)");
        }
        Path directory = Path.of(directoryPart.isEmpty() ? "." : directoryPart);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        return recursive ? walkMatching(directory, namePattern) : listMatching(directory, namePattern);
    }

    private static List<Path> listMatching(Path directory, String namePattern) throws IOException {
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, namePattern)) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate)) {
                    matches.add(candidate);
                }
            }
        }
        return matches;
    }

    private static List<Path> walkMatching(Path directory, String namePattern) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + namePattern);
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .sorted()
                    .toList();
        }
    }

    private List<Path> collectFromDirectory(Path directory) throws IOException {
        try (Stream<Path> walk = recursive ? Files.walk(directory) : Files.list(directory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(InputResolver::hasSchematicExtension)
                    .sorted()
                    .toList();
        }
    }

    private static boolean hasSchematicExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && SCHEMATIC_EXTENSIONS.contains(name.substring(dot + 1));
    }
}
