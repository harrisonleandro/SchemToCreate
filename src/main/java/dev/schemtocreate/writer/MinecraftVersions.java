package dev.schemtocreate.writer;

import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Maps a {@code DataVersion} to the Minecraft release it belongs to.
 *
 * <p>Exists to turn "DataVersion 4440, not 3465" — which means nothing to most people — into
 * "made in 1.21.8, target is 1.20.1". A version gap is the single most common reason a
 * converted schematic looks wrong in game, so naming it plainly is worth a lookup table.
 *
 * <p>The table lists release versions only, and deliberately stops at the last entry the
 * author could verify. Anything past it is reported as "newer than" rather than guessed at,
 * because a confidently wrong version number is worse than an honest range.
 */
public final class MinecraftVersions {

    private static final NavigableMap<Integer, String> RELEASES = new TreeMap<>();

    /** Highest entry in the table; beyond this the answer is a range, not a name. */
    private static final int HIGHEST_KNOWN;

    static {
        RELEASES.put(1631, "1.12.2");
        RELEASES.put(1976, "1.14.4");
        RELEASES.put(2230, "1.15.2");
        RELEASES.put(2586, "1.16.5");
        RELEASES.put(2730, "1.17.1");
        RELEASES.put(2975, "1.18.2");
        RELEASES.put(3120, "1.19.2");
        RELEASES.put(3337, "1.19.4");
        RELEASES.put(3463, "1.20");
        RELEASES.put(3465, "1.20.1");
        RELEASES.put(3578, "1.20.2");
        RELEASES.put(3700, "1.20.4");
        RELEASES.put(3839, "1.20.6");
        RELEASES.put(3953, "1.21");
        RELEASES.put(3955, "1.21.1");
        RELEASES.put(4082, "1.21.3");
        RELEASES.put(4189, "1.21.4");
        RELEASES.put(4325, "1.21.5");
        HIGHEST_KNOWN = RELEASES.lastKey();
    }

    private MinecraftVersions() {
    }

    /**
     * A readable description of {@code dataVersion}.
     *
     * @return an exact release name, a range between two known releases, or a "newer than"
     *         statement when the value is past the end of the table
     */
    public static String describe(int dataVersion) {
        String exact = RELEASES.get(dataVersion);
        if (exact != null) {
            return "Minecraft " + exact;
        }
        if (dataVersion > HIGHEST_KNOWN) {
            return "a version newer than Minecraft " + RELEASES.get(HIGHEST_KNOWN)
                    + " (DataVersion " + dataVersion + ")";
        }
        var below = RELEASES.floorEntry(dataVersion);
        var above = RELEASES.ceilingEntry(dataVersion);
        if (below == null) {
            return "a version older than Minecraft " + above.getValue()
                    + " (DataVersion " + dataVersion + ")";
        }
        return "a snapshot between Minecraft " + below.getValue() + " and " + above.getValue()
                + " (DataVersion " + dataVersion + ")";
    }

    /** True when the source predates the 1.20.1 target and may use since-renamed blocks. */
    public static boolean isOlderThanTarget(int dataVersion) {
        return dataVersion > 0 && dataVersion < CreateWriterOptions.DATA_VERSION_1_20_1;
    }

    /** True when the source postdates 1.20.1 and may use blocks that do not exist there. */
    public static boolean isNewerThanTarget(int dataVersion) {
        return dataVersion > CreateWriterOptions.DATA_VERSION_1_20_1;
    }
}
