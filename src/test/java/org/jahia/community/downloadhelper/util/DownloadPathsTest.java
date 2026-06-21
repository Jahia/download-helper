package org.jahia.community.downloadhelper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadPathsTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("resolves a plain filename to a file directly inside the folder")
    void plainFilename() throws IOException {
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), "report.zip");

        assertThat(resolved.getParentFile().getCanonicalPath())
                .isEqualTo(folder.toFile().getCanonicalPath());
        assertThat(resolved).hasName("report.zip");
    }

    @Test
    @DisplayName("strips directory components and keeps only the base name")
    void stripsDirectoryComponents() throws IOException {
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), "sub/dir/report.zip");

        assertThat(resolved).hasName("report.zip");
        assertThat(resolved.getParentFile().getCanonicalPath())
                .isEqualTo(folder.toFile().getCanonicalPath());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {".", "..", "sub/dir/", "../"})
    @DisplayName("rejects null, empty, path-only and dot/dot-dot filenames")
    void rejectsEmptyOrTraversalOnlyNames(String raw) {
        assertThatThrownBy(() -> DownloadPaths.resolveContainedFile(folder.toString(), raw))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("whitespace-only filename should be rejected")
    void whiteSpaceOnlyFilenameRejected() {
        assertThatThrownBy(() -> DownloadPaths.resolveContainedFile(folder.toString(), "   "))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("a filename whose folder component is exactly the folder name does not escape the folder")
    void folderNameEdge() throws IOException {
        // A filename equal to the containing folder's own name (e.g. "jahia-download-helper")
        // must still land inside that folder, not resolve to it or escape it.
        final String folderName = folder.toFile().getName();
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), folderName + ".zip");

        assertThat(resolved.getCanonicalPath())
                .startsWith(folder.toFile().getCanonicalPath() + File.separator);
        assertThat(resolved).hasName(folderName + ".zip");
    }

    @Test
    @DisplayName("very long filename is accepted when it stays inside the folder")
    void veryLongFilename() throws IOException {
        // 200-char name: well within typical FS limits (255 bytes on ext4/HFS+/NTFS) but exercises
        // the code path with a large input.
        final String longName = "a".repeat(200) + ".zip";
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), longName);

        assertThat(resolved.getCanonicalPath())
                .startsWith(folder.toFile().getCanonicalPath() + File.separator);
        assertThat(resolved).hasName(longName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "../outside.txt",
            "..\\..\\windows\\system32"
    })
    @DisplayName("collapses traversal sequences to a contained base name (never escapes the folder)")
    void traversalCollapsesToContainedName(String raw) throws IOException {
        // FilenameUtils.getName strips path separators (both / and \\), so these collapse to a base
        // name that stays inside the folder. The security invariant is containment, not rejection.
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), raw);

        assertThat(resolved.getCanonicalPath())
                .startsWith(folder.toFile().getCanonicalPath() + File.separator);
        assertThat(resolved.getName()).doesNotContain("..");
    }

    @Test
    @DisplayName("a contained file never resolves outside the download folder")
    void neverEscapesFolder() throws IOException {
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), "data.bin");

        assertThat(resolved.getCanonicalPath())
                .startsWith(folder.toFile().getCanonicalPath() + File.separator);
    }
}
