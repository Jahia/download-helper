package org.jahia.modules.downloadhelper.util;

import org.apache.hc.core5.http.HttpStatus;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure, side-effect-free helpers backing the download SSRF / log-injection defenses.
 *
 * <p>Everything here is deterministic and free of JCR / OSGi / network access (the one method that
 * inspects an {@link InetAddress} accepts an already-resolved address), so it can be unit-tested in
 * isolation. Keeping the security-critical predicates in one tested place avoids the
 * "check-then-trust" drift that the redirect-based SSRF bypass exploited.</p>
 */
public final class UrlSecurityUtils {

    /**
     * Cloud-metadata / well-known SSRF target hostnames that must never be reachable,
     * even from an admin-triggered download.
     */
    private static final Set<String> BLOCKED_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "metadata.google.internal",
            "metadata.goog",
            "metadata")));

    private static final String CLOUD_METADATA_IP = "169.254.169.254";
    private static final int MAX_REDIRECTS = 5;

    private UrlSecurityUtils() {
    }

    public static int maxRedirects() {
        return MAX_REDIRECTS;
    }

    /**
     * Strips CR/LF from a string so attacker-controlled input cannot forge log lines (CWE-117).
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }

    /**
     * @return {@code true} when the host (case-insensitively) is a well-known cloud-metadata hostname.
     */
    public static boolean isBlockedHost(String host) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        return BLOCKED_HOSTS.contains(host.toLowerCase());
    }

    /**
     * @return {@code true} when the resolved address is loopback, link-local, site-local, any-local,
     * multicast, an IPv6 unique-local address (fc00::/7), or the canonical cloud-metadata IP — i.e.
     * must never be contacted.
     */
    public static boolean isNonRoutableAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address)
                || CLOUD_METADATA_IP.equals(address.getHostAddress());
    }

    /**
     * Detects IPv6 unique-local addresses (fc00::/7). {@link InetAddress#isSiteLocalAddress()} only
     * covers the deprecated fec0::/10 range in Java, so modern ULA must be checked explicitly.
     *
     * @return {@code true} when the address is a 16-byte address whose first byte matches fc00::/7.
     */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        final byte[] bytes = address.getAddress();
        return bytes != null && bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * @return {@code true} for the HTTP status codes that carry a {@code Location} redirect.
     */
    public static boolean isRedirectStatus(int statusCode) {
        return statusCode == HttpStatus.SC_MOVED_PERMANENTLY
                || statusCode == HttpStatus.SC_MOVED_TEMPORARILY
                || statusCode == HttpStatus.SC_SEE_OTHER
                || statusCode == HttpStatus.SC_TEMPORARY_REDIRECT
                || statusCode == HttpStatus.SC_PERMANENT_REDIRECT;
    }

    /**
     * Only {@code http}/{@code https} redirect targets are honored; {@code file:}, {@code ftp:},
     * {@code gopher:}, {@code jar:} etc. are rejected to keep the SSRF surface closed.
     */
    public static boolean isAllowedHttpScheme(String url) {
        final String scheme = schemeOf(url);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    /**
     * Resolves a (possibly relative) {@code Location} header against the current absolute URL.
     *
     * @return the absolute redirect target, or {@code null} if it cannot be parsed.
     */
    public static String resolveLocation(String currentUrl, String location) {
        if (currentUrl == null || location == null || location.trim().isEmpty()) {
            return null;
        }
        try {
            return new URI(currentUrl).resolve(location.trim()).toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    /**
     * @return {@code true} only when both URLs parse to the same (case-insensitive) host. Used to
     * decide whether the {@code Authorization} header may be replayed on a redirect.
     */
    public static boolean sameHost(String urlA, String urlB) {
        final String hostA = hostOf(urlA);
        final String hostB = hostOf(urlB);
        return hostA != null && hostA.equalsIgnoreCase(hostB);
    }

    public static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String schemeOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            final String scheme = new URI(url).getScheme();
            return scheme == null ? null : scheme.toLowerCase();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
