package dev.schemtocreate.gui;

import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether to show the window, and prepares the toolkit before doing so.
 *
 * <p>Kept apart from {@code Main} so that the CLI entry point never touches AWT: loading a
 * Swing class on a headless server would be an avoidable failure for a tool whose main use
 * is scripted conversion.
 */
public final class GuiLauncher {

    /** Explicit request, for `java -jar SchemToCreate.jar --gui` from a terminal. */
    public static final String GUI_FLAG = "--gui";

    private GuiLauncher() {
    }

    /**
     * @return true when the window should open: no arguments at all, or an explicit
     *         {@code --gui}, and a display is actually available
     */
    public static boolean shouldLaunch(String[] args) {
        boolean requested = args.length == 0 || List.of(args).contains(GUI_FLAG);
        return requested && isDisplayAvailable();
    }

    /** True when {@code --gui} was asked for but there is nowhere to draw. */
    public static boolean wasRequestedWithoutDisplay(String[] args) {
        return List.of(args).contains(GUI_FLAG) && !isDisplayAvailable();
    }

    public static boolean isDisplayAvailable() {
        try {
            return !GraphicsEnvironment.isHeadless();
        } catch (Throwable e) {
            // A broken or absent AWT installation is the same situation as headless.
            return false;
        }
    }

    /**
     * Applies the platform look and feel, then opens the window with any paths given
     * alongside {@code --gui} already queued.
     */
    public static void launch(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // The cross-platform default is perfectly usable; appearance is not worth failing over.
        }
        SchemToCreateApp.launch(filePaths(args));
    }

    /** Everything that is not a flag, treated as a file or folder to queue. */
    private static List<Path> filePaths(String[] args) {
        List<Path> paths = new ArrayList<>();
        for (String argument : args) {
            if (!argument.startsWith("-")) {
                paths.add(Path.of(argument));
            }
        }
        return paths;
    }
}
