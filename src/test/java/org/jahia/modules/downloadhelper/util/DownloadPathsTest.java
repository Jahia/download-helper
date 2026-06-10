package org.jahia.modules.downloadhelper.util;

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
        assertThat(resolved.getName()).isEqualTo("report.zip");
    }

    @Test
    @DisplayName("strips directory components and keeps only the base name")
    void stripsDirectoryComponents() throws IOException {
        final File resolved = DownloadPaths.resolveContainedFile(folder.toString(), "sub/dir/report.zip");

        assertThat(resolved.getName()).isEqualTo("report.zip");
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
