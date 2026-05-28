package org.jahia.modules.downloadhelper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class UrlSecurityUtilsTest {

    @Nested
    @DisplayName("sanitizeForLog")
    class SanitizeForLog {

        @Test
        @DisplayName("returns null for null input")
        void nullInput() {
            assertThat(UrlSecurityUtils.sanitizeForLog(null)).isNull();
        }

        @Test
        @DisplayName("strips CR and LF so forged log lines cannot be injected")
        void stripsCrLf() {
            final String malicious = "good\r\nERROR injected admin login from 10.0.0.1";
            assertThat(UrlSecurityUtils.sanitizeForLog(malicious))
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .isEqualTo("good__ERROR injected admin login from 10.0.0.1");
        }

        @Test
        @DisplayName("leaves clean input untouched")
        void cleanInput() {
            assertThat(UrlSecurityUtils.sanitizeForLog("https://example.com/file.zip"))
                    .isEqualTo("https://example.com/file.zip");
        }
    }

    @Nested
    @DisplayName("isBlockedHost")
    class IsBlockedHost {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("treats null/empty host as blocked (fail closed)")
        void nullOrEmpty(String host) {
            assertThat(UrlSecurityUtils.isBlockedHost(host)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"metadata", "metadata.google.internal", "metadata.goog", "METADATA.GOOGLE.INTERNAL"})
        @DisplayName("blocks well-known cloud-metadata hostnames case-insensitively")
        void blocksMetadataHosts(String host) {
            assertThat(UrlSecurityUtils.isBlockedHost(host)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"example.com", "store.jahia.com", "metadata.example.com"})
        @DisplayName("allows ordinary hosts")
        void allowsOrdinaryHosts(String host) {
            assertThat(UrlSecurityUtils.isBlockedHost(host)).isFalse();
        }
    }

    @Nested
    @DisplayName("isNonRoutableAddress")
    class IsNonRoutableAddress {

        @Test
        @DisplayName("treats null address as non-routable (fail closed)")
        void nullAddress() {
            assertThat(UrlSecurityUtils.isNonRoutableAddress(null)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "127.0.0.1",          // loopback
                "169.254.169.254",    // cloud metadata (link-local)
                "10.0.0.5",           // site-local
                "192.168.1.10",       // site-local
                "172.16.0.1",         // site-local
                "0.0.0.0",            // any-local
                "224.0.0.1",          // multicast
                "::1",                // IPv6 loopback
                "fe80::1"             // IPv6 link-local
        })
        @DisplayName("rejects loopback / private / metadata / multicast addresses")
        void rejectsNonRoutable(String ip) throws UnknownHostException {
            // Literal IPs do not trigger DNS resolution.
            assertThat(UrlSecurityUtils.isNonRoutableAddress(InetAddress.getByName(ip))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.216.34"})
        @DisplayName("allows public, routable addresses")
        void allowsRoutable(String ip) throws UnknownHostException {
            assertThat(UrlSecurityUtils.isNonRoutableAddress(InetAddress.getByName(ip))).isFalse();
        }
    }

    @Nested
    @DisplayName("isRedirectStatus")
    class IsRedirectStatus {

        @ParameterizedTest
        @ValueSource(ints = {301, 302, 303, 307, 308})
        @DisplayName("recognizes the Location-bearing redirect codes")
        void redirects(int code) {
            assertThat(UrlSecurityUtils.isRedirectStatus(code)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 204, 304, 400, 401, 403, 404, 500})
        @DisplayName("does not treat non-redirect codes as redirects")
        void nonRedirects(int code) {
            assertThat(UrlSecurityUtils.isRedirectStatus(code)).isFalse();
        }
    }

    @Nested
    @DisplayName("isAllowedHttpScheme")
    class IsAllowedHttpScheme {

        @ParameterizedTest
        @ValueSource(strings = {"http://example.com/x", "https://example.com/x", "HTTPS://EXAMPLE.COM/x"})
        @DisplayName("accepts http and https (case-insensitive)")
        void acceptsHttp(String url) {
            assertThat(UrlSecurityUtils.isAllowedHttpScheme(url)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "ftp://example.com/x",
                "file:///etc/passwd",
                "gopher://example.com/",
                "jar:file:///x!/y",
                "/relative/path",
                "not a url"
        })
        @DisplayName("rejects non-http schemes, relative and unparsable URLs")
        void rejectsOthers(String url) {
            assertThat(UrlSecurityUtils.isAllowedHttpScheme(url)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveLocation")
    class ResolveLocation {

        @Test
        @DisplayName("resolves an absolute Location verbatim")
        void absolute() {
            assertThat(UrlSecurityUtils.resolveLocation("https://a.com/x", "https://b.com/y"))
                    .isEqualTo("https://b.com/y");
        }

        @Test
        @DisplayName("resolves a relative Location against the current URL")
        void relative() {
            assertThat(UrlSecurityUtils.resolveLocation("https://a.com/dir/x", "/other/file.zip"))
                    .isEqualTo("https://a.com/other/file.zip");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("returns null for blank locations")
        void blank(String location) {
            assertThat(UrlSecurityUtils.resolveLocation("https://a.com/x", location)).isNull();
        }

        @Test
        @DisplayName("returns null when the current URL is null")
        void nullCurrent() {
            assertThat(UrlSecurityUtils.resolveLocation(null, "https://b.com")).isNull();
        }
    }

    @Nested
    @DisplayName("sameHost")
    class SameHost {

        @Test
        @DisplayName("true for identical hosts regardless of path or case")
        void identical() {
            assertThat(UrlSecurityUtils.sameHost("https://Example.com/a", "https://example.com/b/c")).isTrue();
        }

        @Test
        @DisplayName("false when the redirect points at a different host (credentials must not follow)")
        void different() {
            assertThat(UrlSecurityUtils.sameHost("https://example.com/a", "https://evil.com/a")).isFalse();
        }

        @Test
        @DisplayName("false when either URL has no parseable host")
        void unparseable() {
            assertThat(UrlSecurityUtils.sameHost("https://example.com", "not a url")).isFalse();
        }
    }
}
