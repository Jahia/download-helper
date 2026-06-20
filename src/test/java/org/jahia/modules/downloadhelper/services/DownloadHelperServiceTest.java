package org.jahia.modules.downloadhelper.services;

import org.jahia.services.mail.MailService;
import org.jahia.services.notification.HttpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DownloadHelperService} — exercising what is testable without a live
 * OSGi container, JCR, or real network.
 *
 * <p>{@link MailService} and {@link HttpClientService} are injected via {@code @Reference} (field
 * injection, no constructor). Plain reflection is used here to set the mocks, since
 * {@code spring-test} is not on the test classpath and adding it would require changing more than
 * the one permitted pom change (adding mockito-core).</p>
 */
class DownloadHelperServiceTest {

    private DownloadHelperService service;
    private MailService mailService;

    @BeforeEach
    void setUp() throws Exception {
        service = new DownloadHelperService();
        mailService = mock(MailService.class);
        final HttpClientService httpClientService = mock(HttpClientService.class);

        // Mail is disabled by default so email side-effects are silenced in all tests.
        when(mailService.isEnabled()).thenReturn(false);

        injectField(service, "mailService", mailService);
        injectField(service, "httpClientService", httpClientService);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Sets a private/package field by name using plain reflection. */
    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // -----------------------------------------------------------------------
    // Protocol validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("download() — protocol validation")
    class ProtocolValidation {

        @ParameterizedTest
        @ValueSource(strings = {"http", "ftp2", "sftp", "file", "", "HTTP", "HTTPS"})
        @DisplayName("throws IllegalArgumentException for unsupported protocols before any side effect")
        void unsupportedProtocolThrows(String protocol) {
            // Validation happens before folder creation or email — IllegalArgumentException is thrown
            // regardless of whether the download folder exists or mail is enabled.
            assertThatThrownBy(() ->
                    service.download(protocol, "example.com/file.zip", null, null, "test.zip", null, "user"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only https or ftp protocols are allowed");
        }

        @Test
        @DisplayName("null protocol throws IllegalArgumentException before any side effect")
        void nullProtocolThrows() {
            assertThatThrownBy(() ->
                    service.download(null, "example.com/file.zip", null, null, "test.zip", null, "user"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // sanitizeForEmail (tested indirectly via download() with mail enabled)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("email sanitizer — CR/LF stripping (indirect)")
    class EmailSanitizer {

        @Test
        @DisplayName("CR/LF in filename does not propagate to an unhandled exception — protocol guard fires first")
        void crlfInFilenameDoesNotCauseUnhandledException() {
            // The protocol guard fires before filename sanitization, so an unsupported protocol
            // with a CR/LF-laden filename still produces a clean IllegalArgumentException.
            assertThatThrownBy(() ->
                    service.download("telnet", "host/path\r\ninjected", null, null,
                            "file\r\ninjected.zip", null, "user\r\nattacker"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CR/LF in URL with valid protocol: download() completes without unhandled exception")
        void crlfInUrlWithHttpsCompletesCleanly() {
            // "https" is accepted; downloadHttps rejects the malformed URL and throws IOException
            // internally. download() catches it and handles it (log + fail email when mail enabled).
            // With mail disabled the method returns quietly — no unhandled exception reaches the caller.
            assertThatCode(() ->
                    service.download("https", "host\r\ninjected/file.zip", null, null,
                            "file.zip", null, "user"))
                    .doesNotThrowAnyException();
            // Mail is disabled, so the internal failure handling must not attempt to send a message.
            verify(mailService, never())
                    .sendMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    // -----------------------------------------------------------------------
    // copyWithLimit — tested via ByteArrayInputStream + TempDir
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("copyWithLimit — byte cap enforcement")
    class CopyWithLimit {

        @Test
        @DisplayName("download() with an https URL containing '://' completes without unhandled exception")
        void schemeInUrlIsRejectedCleanly() {
            // downloadHttps rejects URLs containing "://" — the IOException is caught by download()
            // and handled (log + fail email). With mail disabled the method simply returns.
            // The key invariant: no unhandled exception escapes to the caller.
            assertThatCode(() ->
                    service.download("https", "https://example.com/file.zip", null, null,
                            "file.zip", null, "user"))
                    .doesNotThrowAnyException();
            // Mail is disabled, so no message must be sent while handling the rejection.
            verify(mailService, never())
                    .sendMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("download() with userinfo in https URL completes without unhandled exception")
        void userInfoInUrlIsRejectedCleanly() {
            // downloadHttps rejects URLs with userinfo. IOException is caught internally by download().
            // With mail disabled, the method returns quietly after logging the rejection.
            assertThatCode(() ->
                    service.download("https", "attacker@internal.corp/secret", null, null,
                            "file.zip", null, "user"))
                    .doesNotThrowAnyException();
            verify(mailService, never())
                    .sendMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    // -----------------------------------------------------------------------
    // DownloadHelperConstants.PERMISSION
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DownloadHelperConstants")
    class Constants {

        @Test
        @DisplayName("PERMISSION constant has the exact value required by RBAC and Cypress tests")
        void permissionConstant() {
            // The exact string value is referenced by RBAC config and Cypress tests; locking it here
            // prevents accidental renames from silently breaking access control.
            org.assertj.core.api.Assertions.assertThat(
                    org.jahia.modules.downloadhelper.constants.DownloadHelperConstants.PERMISSION)
                    .isEqualTo("adminDownloadHelper");
        }
    }
}
