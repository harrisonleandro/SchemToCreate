package dev.schemtocreate.cli;

import dev.schemtocreate.converter.ConversionOptions;
import dev.schemtocreate.converter.SchematicConverter;
import dev.schemtocreate.util.Formats;
import dev.schemtocreate.writer.CreateWriterOptions;
import dev.schemtocreate.writer.StructureVoidPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;

/**
 * The {@code schemtocreate} command.
 *
 * <p>Holds option definitions and turns them into a {@link ConversionOptions}; the actual
 * work happens in {@link SchematicConverter} and {@link BatchRunner}.
 */
@Command(
        name = "schemtocreate",
        mixinStandardHelpOptions = true,
        versionProvider = ConvertCommand.VersionProvider.class,
        sortOptions = false,
        usageHelpAutoWidth = true,
        header = "Converts WorldEdit Sponge schematics (.schem) to Create schematics (.nbt).",
        description = {
                "",
                "Produces a GZIP compressed vanilla structure file, the format Create 6.0.8",
                "reads in the Schematic Table and prints with the Schematicannon. Runs fully",
                "offline: no Minecraft, no WorldEdit, no mod loader.",
                ""
        },
        footer = {
                "",
                "Examples:",
                "  schemtocreate house.schem",
                "  schemtocreate house.schem output.nbt",
                "  schemtocreate *.schem --output-dir converted/",
                "  schemtocreate --recursive builds/ --threads 8 --overwrite",
                ""
        })
public final class ConvertCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger("SchemToCreate");

    static final int EXIT_OK = 0;
    static final int EXIT_FAILED = 1;
    static final int EXIT_USAGE = 2;

    @Parameters(
            arity = "1..*",
            paramLabel = "<input>",
            description = {
                    "Input files, directories or glob patterns.",
                    "Two plain paths are read as <input> <output>."
            })
    private List<String> inputs;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>",
            description = "Output file. Only valid with a single input.")
    private Path output;

    @Option(names = {"-d", "--output-dir"}, paramLabel = "<dir>",
            description = "Directory for outputs. Defaults to each input's own directory.")
    private Path outputDirectory;

    @Option(names = {"-r", "--recursive"},
            description = "Descend into subdirectories when an input is a directory.")
    private boolean recursive;

    @Option(names = "--overwrite", description = "Replace existing output files.")
    private boolean overwrite;

    @Option(names = "--entities", paramLabel = "<true|false>", arity = "1",
            description = {
                    "Write the entities list. Default: ${DEFAULT-VALUE}.",
                    "The Schematicannon prints entities in a final stage and consumes a",
                    "matching item for each, so it stalls without those materials."
            })
    private boolean entities = true;

    @Option(names = "--skip-air",
            description = {
                    "Omit air blocks. Smaller output, but the Schematicannon will no longer",
                    "clear those positions in its replace modes. Costs a second pass."
            })
    private boolean skipAir;

    @Option(names = "--structure-void", paramLabel = "<air|keep>",
            description = {
                    "Treatment of minecraft:structure_void. Default: ${DEFAULT-VALUE},",
                    "matching what Create itself writes."
            })
    private StructureVoidPolicy structureVoid = StructureVoidPolicy.AIR;

    @Option(names = "--compression", paramLabel = "<1-9>",
            description = "GZIP level. Default: ${DEFAULT-VALUE}.")
    private int compression = 6;

    @Option(names = "--author", paramLabel = "<name>",
            description = "Value for the optional 'author' field. Omitted by default.")
    private String author;

    @Option(names = "--repair-signs", paramLabel = "<true|false>", arity = "1",
            description = {
                    "Rewrite sign text from Minecraft 1.21.5+ into the JSON components",
                    "1.20.1 expects. Default: ${DEFAULT-VALUE}. Leaving it off keeps the",
                    "source bytes, but one such sign aborts placement of the whole",
                    "structure and Create only says \"Failed to load Schematic\"."
            })
    private boolean repairSigns = true;

    @Option(names = "--replace", paramLabel = "<from=to>",
            description = {
                    "Substitute a block throughout the build. Repeatable.",
                    "For a schematic built in a newer Minecraft than yours, map the blocks",
                    "your version lacks onto ones it has, e.g.",
                    "  --replace minecraft:pale_oak_planks=minecraft:spruce_planks",
                    "Block properties are carried over."
            })
    private Map<String, String> replacements = new LinkedHashMap<>();

    @Option(names = "--data-version", paramLabel = "<n>",
            description = {
                    "Force the DataVersion field. By default the source's value is kept,",
                    "falling back to 3465 (Minecraft 1.20.1)."
            })
    private Integer dataVersion;

    @Option(names = "--threads", paramLabel = "<n>",
            description = {
                    "Files to convert in parallel. Default: ${DEFAULT-VALUE} (all cores).",
                    "A single file is always converted by one thread."
            })
    private int threads = Runtime.getRuntime().availableProcessors();

    @Option(names = "--stream-threshold", paramLabel = "<MiB>",
            description = {
                    "Block data larger than this is streamed from the source file instead",
                    "of buffered. Default: ${DEFAULT-VALUE} MiB."
            })
    private int streamThresholdMiB = 32;

    @Option(names = {"-v", "--verbose"}, description = "Report palette, entity and size details.")
    private boolean verbose;

    @Option(names = "--debug", description = "Log every step, with timestamps and stack traces.")
    private boolean debug;

    @Option(names = "--benchmark", description = "Report throughput and peak heap usage.")
    private boolean benchmark;

    @Option(names = "--list-palette",
            description = {
                    "List every distinct block the build uses, commonest first.",
                    "Use it to spot blocks your Minecraft version does not have: those",
                    "become air on load, silently."
            })
    private boolean listPalette;

    @Option(names = {"-q", "--quiet"}, description = "Only report warnings and errors.")
    private boolean quiet;

    @Option(names = "--dry-run", description = "Resolve inputs and outputs, then stop.")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        LoggingConfigurator.apply(debug, quiet);
        if (compression < 1 || compression > 9) {
            LOG.error("--compression must be between 1 and 9, got {}", compression);
            return EXIT_USAGE;
        }
        if (threads < 1) {
            LOG.error("--threads must be at least 1, got {}", threads);
            return EXIT_USAGE;
        }

        ExplicitPair pair = ExplicitPair.detect(inputs, output);
        List<Path> files = new InputResolver(recursive).resolve(pair.inputs());
        if (pair.output() != null && files.size() > 1) {
            LOG.error("An explicit output file cannot be combined with {} inputs", files.size());
            return EXIT_USAGE;
        }
        LOG.debug("Resolved {} input file(s)", files.size());

        UnaryOperator<Path> outputResolver = resolverFor(pair.output());
        if (dryRun) {
            return reportDryRun(files, outputResolver);
        }
        return runBatch(files, outputResolver);
    }

    private int runBatch(List<Path> files, UnaryOperator<Path> outputResolver) throws Exception {
        SchematicConverter converter = new SchematicConverter(buildOptions());
        BatchRunner.Outcome outcome = new BatchRunner(converter, threads, verbose, benchmark,
                listPalette).run(files, outputResolver);
        if (files.size() > 1 || outcome.failed() > 0 || outcome.skipped() > 0) {
            LOG.info("Converted {} of {} file(s): {} blocks, {} written{}{}",
                    outcome.converted(), files.size(),
                    Formats.count(outcome.totalBlocks()), Formats.bytes(outcome.totalBytes()),
                    outcome.skipped() > 0 ? ", " + outcome.skipped() + " skipped" : "",
                    outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : "");
        }
        if (benchmark && outcome.peakHeap() > 0) {
            LOG.info("Peak heap across the run: {}", Formats.bytes(outcome.peakHeap()));
        }
        return outcome.isSuccess() ? EXIT_OK : EXIT_FAILED;
    }

    private int reportDryRun(List<Path> files, UnaryOperator<Path> outputResolver) {
        for (Path file : files) {
            Path target = outputResolver.apply(file);
            LOG.info("{} -> {}{}", file, target,
                    Files.exists(target) && !overwrite ? "  [exists, would be skipped]" : "");
        }
        LOG.info("{} file(s) would be converted", files.size());
        return EXIT_OK;
    }

    private ConversionOptions buildOptions() {
        CreateWriterOptions writerOptions = CreateWriterOptions.defaults()
                .withIncludeEntities(entities)
                .withSkipAir(skipAir)
                .withStructureVoid(structureVoid)
                .withCompressionLevel(compression)
                .withAuthor(author)
                .withDataVersionOverride(dataVersion)
                .withBlockReplacements(normalizeReplacements())
                .withRepairSignText(repairSigns);
        return ConversionOptions.defaults()
                .withWriter(writerOptions)
                .withOverwrite(overwrite)
                .withInlineThreshold((long) streamThresholdMiB * 1024 * 1024);
    }

    /** Adds the implicit {@code minecraft:} namespace so short names work on the command line. */
    private Map<String, String> normalizeReplacements() {
        Map<String, String> normalized = new LinkedHashMap<>();
        replacements.forEach((from, to) ->
                normalized.put(withNamespace(from), withNamespace(to)));
        return normalized;
    }

    private static String withNamespace(String name) {
        String trimmed = name.trim();
        return trimmed.indexOf(':') < 0 ? "minecraft:" + trimmed : trimmed;
    }

    private UnaryOperator<Path> resolverFor(Path explicitOutput) {
        if (explicitOutput != null) {
            return input -> explicitOutput;
        }
        if (outputDirectory != null) {
            return input -> outputDirectory.resolve(
                    SchematicConverter.defaultOutputFor(input).getFileName());
        }
        return SchematicConverter::defaultOutputFor;
    }

    /**
     * Resolves the {@code input.schem output.nbt} shorthand.
     *
     * <p>Two positional arguments are ambiguous: they could be two inputs or an input and an
     * output. The second is treated as an output only when it names a {@code .nbt} file that
     * is not itself an existing schematic, which is what the documented shorthand means and
     * keeps {@code schemtocreate a.schem b.schem} working.
     */
    private record ExplicitPair(List<String> inputs, Path output) {

        static ExplicitPair detect(List<String> arguments, Path explicitOutput) {
            if (explicitOutput != null || arguments.size() != 2) {
                return new ExplicitPair(arguments, explicitOutput);
            }
            String second = arguments.get(1);
            Path candidate = Path.of(second);
            boolean looksLikeOutput = second.toLowerCase(java.util.Locale.ROOT).endsWith(".nbt")
                    && !Files.isDirectory(candidate);
            return looksLikeOutput
                    ? new ExplicitPair(List.of(arguments.get(0)), candidate)
                    : new ExplicitPair(arguments, null);
        }
    }

    /** Reads the version from the jar manifest, falling back to a development marker. */
    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            Package pkg = ConvertCommand.class.getPackage();
            String version = pkg == null ? null : pkg.getImplementationVersion();
            return new String[]{
                    "SchemToCreate " + (version == null ? "(development build)" : version),
                    "Target: Minecraft 1.20.1 / Create 6.0.8"
            };
        }
    }
}
