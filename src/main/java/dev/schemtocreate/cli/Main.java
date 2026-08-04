package dev.schemtocreate.cli;

import dev.schemtocreate.gui.GuiLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Entry point.
 *
 * <p>Deliberately thin: it wires up argument parsing, decides the process exit code and
 * routes unexpected failures to the log. Validation lives in {@link ConvertCommand} and
 * {@link dev.schemtocreate.converter.SchematicConverter}, conversion in the reader, writer
 * and converter layers.
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger("SchemToCreate");

    private Main() {
    }

    public static void main(String[] args) {
        if (GuiLauncher.shouldLaunch(args)) {
            // No System.exit: Swing's event dispatch thread keeps the JVM alive, and exiting
            // here would close the window the instant it opened.
            GuiLauncher.launch(args);
            return;
        }
        if (GuiLauncher.wasRequestedWithoutDisplay(args)) {
            LOG.error("--gui was requested but no display is available on this machine");
            System.exit(ConvertCommand.EXIT_USAGE);
        }
        System.exit(run(args));
    }

    /**
     * Runs the CLI and returns the exit code, without terminating the JVM or touching the
     * window. Arguments are always handled as a command line here, so no-arguments prints
     * usage rather than opening the app; {@link #main} is what decides between the two.
     */
    public static int run(String[] args) {
        CommandLine command = new CommandLine(new ConvertCommand())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler(Main::handleFailure);
        if (args.length == 0) {
            command.usage(System.out);
            return ConvertCommand.EXIT_USAGE;
        }
        return command.execute(args);
    }

    private static int handleFailure(Exception e, CommandLine command,
                                     CommandLine.ParseResult parseResult) {
        // The message alone is what a user can act on; the stack trace is behind --debug.
        LOG.error("{}", e.getMessage() == null ? e.toString() : e.getMessage());
        LOG.debug("Unhandled failure", e);
        return ConvertCommand.EXIT_FAILED;
    }
}
