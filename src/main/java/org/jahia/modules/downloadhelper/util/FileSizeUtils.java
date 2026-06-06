package org.jahia.modules.downloadhelper.util;

import java.text.DecimalFormat;

/**
 * Shared utility for formatting byte counts as human-readable strings (e.g. "1.5 MiB").
 *
 * <p>Extracted from the three original inlined copies in {@code DownloadHelperService},
 * {@code GqlDownloadedFile}, and {@code DownloadHelperQueryExtension} to eliminate duplication.</p>
 *
 * <p>Supports files up to and beyond 1 PiB by capping the unit index so that values too large to
 * represent in TiB are displayed as an astronomically large TiB number rather than throwing an
 * {@link ArrayIndexOutOfBoundsException}.</p>
 */
public final class FileSizeUtils {

    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};
    private static final int KILO_CONSTANT = 1024;

    private FileSizeUtils() {
    }

    /**
     * Formats {@code bytes} as a human-readable string with one decimal place.
     *
     * @param bytes the number of bytes (non-positive values are returned as {@code "0 B"})
     * @return a human-readable size string, e.g. {@code "1.5 MiB"}
     */
    public static String format(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        final int digitGroups = Math.min(
                (int) (Math.log10(bytes) / Math.log10(KILO_CONSTANT)),
                UNITS.length - 1);
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(KILO_CONSTANT, digitGroups))
                + " " + UNITS[digitGroups];
    }
}
