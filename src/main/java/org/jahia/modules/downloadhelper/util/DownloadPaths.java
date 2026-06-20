package org.jahia.modules.downloadhelper.util;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

/**
 * Resolves a user-supplied filename to a {@link File} that is provably contained within the
 * download folder, rejecting path-traversal attempts.
 *
 * <p>{@link FilenameUtils#getName(String)} strips directory components but does <strong>not</strong>
 * neutralize a bare {@code ".."} (it has no separator, so it is returned verbatim) nor a {@code null}.
 * {@code new File(folder, "..")} would then resolve to the parent of the download folder. This helper
 * closes that gap with a canonical-path containment check, mirroring the protection already applied to
 * the delete mutation so that both the download and delete code paths share one tested implementation.</p>
 */
public final class DownloadPaths {

    private DownloadPaths() {
    }

    /**
     * Resolves {@code rawFilename} against {@code folderPath} and verifies the result stays inside the
     * folder.
     *
     * @param folderPath  the absolute download folder path (trusted, hardcoded by the module)
     * @param rawFilename the user-supplied filename (may be {@code null}, empty, path-only, or traversal)
     * @return the contained target {@link File}
     * @throws IOException if the filename is empty/path-only, escapes the folder, or cannot be resolved
     */
    public static File resolveContainedFile(String folderPath, String rawFilename) throws IOException {
        final String safeName = FilenameUtils.getName(rawFilename);
        if (safeName == null || safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new IOException("Invalid or empty filename");
        }

        final File folder = new File(folderPath);
        final File target = new File(folder, safeName);
        final String canonicalFolder = folder.getCanonicalPath();
        final String canonicalTarget = target.getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalFolder + File.separator)) {
            throw new IOException("Filename resolves outside the download folder");
        }
        return target;
    }
}
