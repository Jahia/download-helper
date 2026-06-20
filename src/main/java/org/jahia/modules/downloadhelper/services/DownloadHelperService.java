package org.jahia.modules.downloadhelper.services;

import org.apache.commons.net.util.Base64;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.jahia.modules.downloadhelper.constants.Email;
import org.jahia.modules.downloadhelper.util.DownloadPaths;
import org.jahia.modules.downloadhelper.util.FileSizeUtils;
import org.jahia.modules.downloadhelper.util.UrlSecurityUtils;
import org.jahia.services.mail.MailService;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.jahia.modules.downloadhelper.util.UrlSecurityUtils.sanitizeForLog;

@Component(service = DownloadHelperService.class)
public class DownloadHelperService {

    public static final String DOWNLOAD_FOLDER_PATH = "/tmp/jahia-download-helper";
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperService.class);
    private static final int KILO_CONSTANT = 1024;
    private static final String MSG_COULD_NOT_CREATE_FOLDER = "Could not create download folder: {}";
    private static final String DATE_PATTERN = "yyyy/MM/dd 'at' HH:mm:ss z";
    private static final int DOWNLOAD_POOL_SIZE = 2;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    /**
     * Basic RFC-5321-ish email validation: a single address with no whitespace / control chars.
     * Deliberately conservative — it only needs to reject obviously malformed or injection-bearing CC values.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[^\\s@]{2,}$");

    /**
     * Bundle-lifecycle-tied executor for download work. Owning the pool here (rather than spawning a raw
     * {@code new Thread()} per request) lets us shut downloads down cleanly on {@code @Deactivate}.
     */
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(DOWNLOAD_POOL_SIZE);

    /**
     * SimpleDateFormat is not thread-safe; downloads run in separate threads, so we build a fresh
     * formatter on demand. A ThreadLocal would risk classloader leaks in OSGi when the bundle is refreshed.
     */
    private static String formatNow() {
        return new SimpleDateFormat(DATE_PATTERN).format(new Date());
    }

    @Reference
    private MailService mailService;

    @Reference
    private HttpClientService httpClientService;

    private static String formatSize(long bytes) {
        return FileSizeUtils.format(bytes);
    }

    /**
     * Rejects URLs that resolve to loopback, link-local, site-local, any-local, multicast addresses,
     * the cloud-metadata IP, or well-known cloud-metadata hostnames. Defense-in-depth against SSRF
     * even though the caller already requires the {@code adminSystemInfos} permission. The classification
     * predicates live in {@link UrlSecurityUtils} so they can be unit-tested without DNS.
     */
    private static void assertSafeHost(String host) throws IOException {
        if (host == null || host.isEmpty()) {
            throw new IOException("Empty host is not allowed");
        }
        final String lowerHost = host.toLowerCase();
        if (UrlSecurityUtils.isBlockedHost(host)) {
            throw new IOException("Host is blocked: " + lowerHost);
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Unknown host: " + lowerHost, e);
        }
        for (InetAddress address : addresses) {
            if (UrlSecurityUtils.isNonRoutableAddress(address)) {
                throw new IOException("Host resolves to a non-routable / blocked address: " + lowerHost);
            }
        }
    }

    private static String extractHost(String completeUrl) throws IOException {
        try {
            final URI uri = new URI(completeUrl);
            return uri.getHost();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL", e);
        }
    }

    @Activate
    public void activate() {
        ensureDownloadFolder();
    }

    @Deactivate
    public void deactivate() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ensures the download folder exists, creating it if necessary. Centralizes the folder-creation
     * logic shared by {@link #activate()} and {@link #download}.
     *
     * @return {@code true} if the folder exists (or was created), {@code false} if it could not be created.
     */
    private boolean ensureDownloadFolder() {
        final File downloadFolder = new File(DOWNLOAD_FOLDER_PATH);
        if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
            LOGGER.warn(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
            return false;
        }
        return true;
    }

    /**
     * Submits a download to the bundle-owned executor. Replaces per-request raw threads so download
     * work is tied to the bundle lifecycle and shut down on {@code @Deactivate}.
     */
    public void submitDownload(String protocol, String url, String login, String password,
            String filename, String ccEmail, String user) {
        try {
            downloadExecutor.submit(() -> {
                try {
                    download(protocol, url, login, password, filename, ccEmail, user);
                } catch (IOException | RuntimeException e) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Async download failed for url={} filename={} user={}",
                                sanitizeForLog(url), sanitizeForLog(filename), sanitizeForLog(user), e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // The executor has been (or is being) shut down on @Deactivate; a late submitDownload
            // (e.g. a GraphQL mutation racing bundle deactivation) must not propagate an unchecked
            // exception to the client. Log and return gracefully instead.
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Download request rejected: the download executor is shutting down for user={}",
                        sanitizeForLog(user));
            }
        }
    }

    public void download(String protocol, String url, String login, String password,
            String filename, String ccEmail, String user) throws IOException {
        // Validate protocol early, before any side effects (folder creation, emails, network).
        if (!"https".equals(protocol) && !"ftp".equals(protocol)) {
            throw new IllegalArgumentException("Only https or ftp protocols are allowed");
        }

        if (!ensureDownloadFolder()) {
            LOGGER.error(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
            sendFolderCreationFailedEmail(url, filename, ccEmail, user);
            return;
        }

        final File targetFile;
        try {
            targetFile = DownloadPaths.resolveContainedFile(DOWNLOAD_FOLDER_PATH, filename);
        } catch (IOException ex) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Rejected unsafe filename for download asked by {}: {}",
                        sanitizeForLog(user), sanitizeForLog(filename));
            }
            sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_FAILED_SUBJECT);
            return;
        }
        boolean result = false;
        try {
            if ("https".equals(protocol)) {
                result = downloadHttps(url, login, password, filename, ccEmail, user, targetFile);
            } else {
                result = downloadFtp(url, login, password, filename, ccEmail, user, targetFile);
            }
        } catch (IOException | RuntimeException ex) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Download of {} to {} asked by {} has failed",
                        sanitizeForLog(url), sanitizeForLog(filename), sanitizeForLog(user), ex);
            }
        } finally {
            if (result) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Download of {} to {} asked by {} is complete",
                            sanitizeForLog(url), sanitizeForLog(filename), sanitizeForLog(user));
                }
                sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_COMPLETED_SUBJECT);
            } else {
                sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_FAILED_SUBJECT);
            }
        }
    }

    private boolean downloadHttps(String url, String login, String password, String filename,
            String ccEmail, String user, File targetFile) throws IOException {
        // Reject scheme-prefixed input to avoid "https://https://..." confusion.
        if (url.contains("://")) {
            throw new IOException("URL must not contain a scheme prefix; provide host and path only");
        }
        final String initialUrl = "https://" + url;
        // Reject userinfo (e.g. "evil.com@internal-host") — it is never legitimate for a download URL
        // and obfuscates which host the HTTP client will actually connect to.
        final URI initialUri;
        try {
            initialUri = new URI(initialUrl);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + sanitizeForLog(url), e);
        }
        if (initialUri.getUserInfo() != null) {
            throw new IOException("URL must not contain userinfo (user@host is not allowed)");
        }
        assertSafeHost(initialUri.getHost());
        sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_ASKED_SUBJECT);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Download of {} to {} asked by {}",
                    sanitizeForLog(initialUrl), sanitizeForLog(filename), sanitizeForLog(user));
        }

        final CloseableHttpClient httpClient = httpClientService.getHttpClient(initialUrl);
        String currentUrl = initialUrl;
        // The shared HttpClient follows redirects automatically, which would let a remote server
        // 30x-redirect to an internal / metadata host and bypass assertSafeHost (and replay the
        // Basic credentials to it). We disable automatic redirects and follow them ourselves,
        // re-validating every hop and only sending credentials to the originally validated host.
        for (int hop = 0; hop <= UrlSecurityUtils.maxRedirects(); hop++) {
            assertSafeHost(extractHost(currentUrl));
            final HttpGet httpGet = buildHttpGet(currentUrl, initialUrl, login, password);
            final HopResult result = executeHop(httpGet, httpClient, currentUrl, filename, url, ccEmail, user, targetFile);
            if (result.nextUrl() != null) {
                currentUrl = result.nextUrl();
                continue;
            }
            return result.done();
        }

        throw new IOException("Too many redirects (max " + UrlSecurityUtils.maxRedirects() + ")");
    }

    /**
     * Carries the outcome of a single HTTP hop: either a redirect target to follow next
     * ({@code nextUrl} non-null, {@code done} unused) or a terminal result ({@code nextUrl}
     * null, {@code done} is the success flag).
     */
    private static final class HopResult {
        private final String nextUrl;
        private final boolean done;

        private HopResult(String nextUrl, boolean done) {
            this.nextUrl = nextUrl;
            this.done = done;
        }

        String nextUrl() { return nextUrl; }

        boolean done() { return done; }

        static HopResult redirect(String url) { return new HopResult(url, false); }

        static HopResult terminal(boolean success) { return new HopResult(null, success); }
    }

    /**
     * Builds the {@link HttpGet} for one hop: disables automatic redirects, conditionally attaches
     * Basic credentials only when the target host matches the initial host (no cross-host credential
     * leak), and sets the User-Agent.
     */
    private static HttpGet buildHttpGet(String currentUrl, String initialUrl,
            String login, String password) {
        final HttpGet httpGet = new HttpGet(currentUrl);
        httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
        if (UrlSecurityUtils.sameHost(currentUrl, initialUrl)
                && login != null && !login.isEmpty() && password != null && !password.isEmpty()) {
            httpGet.addHeader("Authorization", "Basic " + new String(
                    Base64.encodeBase64((login + ":" + password).getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));
        }
        httpGet.addHeader("User-Agent", "Jahia - Download Helper");
        return httpGet;
    }

    /**
     * Executes a single HTTP hop and returns a {@link HopResult}: a redirect target to follow, or a
     * terminal success/failure. All security invariants (redirect-scheme check via
     * {@code nextRedirectUrl}, size cap via {@code hasEnoughSpace}/{@code copyWithLimit}) are preserved.
     */
    @SuppressWarnings("java:S107") // private per-hop helper; the parameters are the irreducible download context
    private HopResult executeHop(HttpGet httpGet, CloseableHttpClient httpClient,
            String currentUrl, String filename, String url, String ccEmail, String user,
            File targetFile) throws IOException {
        try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
            final int statusCode = httpResponse.getCode();
            if (UrlSecurityUtils.isRedirectStatus(statusCode)) {
                return HopResult.redirect(nextRedirectUrl(currentUrl, httpResponse));
            }

            final HttpEntity entity = httpResponse.getEntity();
            if (entity != null && HttpStatus.SC_OK == statusCode) {
                if (!hasEnoughSpace(entity.getContentLength(), url, filename, ccEmail, user)) {
                    return HopResult.terminal(false);
                }
                copyWithLimit(entity.getContent(), targetFile, entity.getContentLength());
                return HopResult.terminal(true);
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Download of {} to {} asked by {} has failed with status {}",
                        sanitizeForLog(currentUrl), sanitizeForLog(filename), sanitizeForLog(user), statusCode);
            }
            return HopResult.terminal(false);
        }
    }

    /**
     * Resolves and validates the {@code Location} of a redirect response. Only absolute http(s)
     * targets are honored; the returned URL still gets {@code assertSafeHost}-checked on the next loop hop.
     */
    private static String nextRedirectUrl(String currentUrl, CloseableHttpResponse response) throws IOException {
        final Header locationHeader = response.getFirstHeader("Location");
        if (locationHeader == null) {
            throw new IOException("Redirect response without a Location header");
        }
        final String resolved = UrlSecurityUtils.resolveLocation(currentUrl, locationHeader.getValue());
        if (resolved == null || !UrlSecurityUtils.isAllowedHttpScheme(resolved)) {
            throw new IOException("Refusing to follow redirect to a non-http(s) or unparsable location");
        }
        return resolved;
    }

    private boolean downloadFtp(String url, String login, String password, String filename,
            String ccEmail, String user, File targetFile) throws IOException {
        final String safeLogUrl = String.format("ftp://%s:XXXXX@%s",
                login == null ? "" : login, url);
        sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_ASKED_SUBJECT);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Download of {} to {} asked by {}",
                    sanitizeForLog(safeLogUrl), sanitizeForLog(filename), sanitizeForLog(user));
        }

        final String encodedLogin = login == null ? "" : URLEncoder.encode(login, StandardCharsets.UTF_8);
        final String encodedPassword = password == null ? "" : URLEncoder.encode(password, StandardCharsets.UTF_8);
        final String ftpUrl = String.format("ftp://%s:%s@%s", encodedLogin, encodedPassword, url);
        assertSafeHost(extractHost(ftpUrl));
        final URLConnection urlConnection = new URL(ftpUrl).openConnection();
        boolean streamHandedOff = false;
        try {
            final long advertisedLength = urlConnection.getContentLengthLong();
            if (!hasEnoughSpace(advertisedLength, url, filename, ccEmail, user)) {
                return false;
            }
            // copyWithLimit owns (and closes) the stream once it has it.
            final InputStream inputStream = urlConnection.getInputStream();
            streamHandedOff = true;
            copyWithLimit(inputStream, targetFile, advertisedLength);
            return true;
        } finally {
            // On the early return-false / exception-before-handoff paths the connection's stream was
            // never opened-and-closed by copyWithLimit, so release it here. For an FTP URLConnection
            // closing its input stream is the only way to tear the connection down (it has no public
            // disconnect()); for an HTTP-backed URLConnection we additionally call disconnect().
            if (!streamHandedOff) {
                closeConnection(urlConnection);
            }
        }
    }

    /**
     * Tears down a {@link URLConnection} that was opened but whose input stream was not consumed.
     * Avoids leaking the FTP control/data connection on the insufficient-space early-return path.
     */
    private static void closeConnection(URLConnection urlConnection) {
        if (urlConnection instanceof java.net.HttpURLConnection) {
            ((java.net.HttpURLConnection) urlConnection).disconnect();
            return;
        }
        try {
            urlConnection.getInputStream().close();
        } catch (IOException e) {
            LOGGER.warn("Could not close download connection", e);
        }
    }

    /**
     * Copies the stream to the target file with a hard byte cap enforced in <em>all</em> cases. The cap
     * is the currently usable disk space, further bounded by the advertised content length when it is
     * positive. This prevents a server that advertises a small {@code Content-Length} but then streams
     * more bytes from filling the disk. On overrun the partial file is deleted and the copy aborts.
     */
    private void copyWithLimit(InputStream rawStream, File targetFile, long contentLength) throws IOException {
        final long usableSpace = Files.getFileStore(Paths.get(DOWNLOAD_FOLDER_PATH)).getUsableSpace();
        final long maxBytes = (contentLength > 0) ? Math.min(contentLength, usableSpace) : usableSpace;
        boolean exceeded = false;
        try (InputStream inputStream = rawStream;
                OutputStream out = Files.newOutputStream(targetFile.toPath())) {
            final byte[] buffer = new byte[8 * KILO_CONSTANT];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) {
                    exceeded = true;
                    break;
                }
                out.write(buffer, 0, read);
            }
        }
        // Delete only after the OutputStream is closed so the partial file is removable on every OS.
        if (exceeded) {
            Files.deleteIfExists(targetFile.toPath());
            throw new IOException("Download exceeds the allowed size; aborted");
        }
    }

    /**
     * Strips CR/LF and other control characters from a user-derived value before it is embedded in an
     * email subject/body, closing email header/content-injection (CWE-93). Returns {@code ""} for null.
     */
    private static String sanitizeForEmail(String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString();
    }

    /**
     * Validates a CC email address. Returns the trimmed address when it is a single, well-formed
     * (RFC-5321-ish) address with no control characters; otherwise logs a sanitized warning and
     * returns {@code null} so the CC is skipped (the download itself is not aborted).
     */
    private static String validatedCc(String ccEmail) {
        if (ccEmail == null || ccEmail.trim().isEmpty()) {
            return null;
        }
        final String trimmed = ccEmail.trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Skipping invalid CC email address: {}", sanitizeForLog(ccEmail));
            }
            return null;
        }
        return trimmed;
    }

    private void sendEmail(String url, String filename, String ccEmail, String user, String subject) {
        if (mailService.isEnabled()) {
            final String safeSubject = sanitizeForEmail(subject);
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), validatedCc(ccEmail), null,
                    safeSubject,
                    String.format(Email.DOWNLOAD_BODY, safeSubject, formatNow(),
                            sanitizeForEmail(user), sanitizeForEmail(filename), sanitizeForEmail(url)));
        }
    }

    private boolean hasEnoughSpace(long contentLength, String url, String filename,
            String ccEmail, String user) throws IOException {
        if (contentLength <= 0) {
            return true;
        }

        if (!ensureDownloadFolder()) {
            LOGGER.error(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
            sendFolderCreationFailedEmail(url, filename, ccEmail, user);
            return false;
        }

        final long freeBytes = Files.getFileStore(Paths.get(DOWNLOAD_FOLDER_PATH)).getUsableSpace();
        if (freeBytes < contentLength) {
            LOGGER.error("Not enough disk space in {}: required={}, available={}",
                    DOWNLOAD_FOLDER_PATH, contentLength, freeBytes);
            sendInsufficientSpaceEmail(url, filename, ccEmail, user, contentLength, freeBytes);
            return false;
        }

        return true;
    }

    private void sendInsufficientSpaceEmail(String url, String filename, String ccEmail,
            String user, long contentLength, long freeBytes) {
        if (mailService.isEnabled()) {
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), validatedCc(ccEmail), null,
                    Email.DOWNLOAD_INSUFFICIENT_SPACE_SUBJECT,
                    String.format(Email.DOWNLOAD_INSUFFICIENT_SPACE_BODY,
                            DOWNLOAD_FOLDER_PATH, formatSize(contentLength), formatSize(freeBytes),
                            formatNow(), sanitizeForEmail(user), sanitizeForEmail(filename),
                            sanitizeForEmail(url)));
        }
    }

    private void sendFolderCreationFailedEmail(String url, String filename, String ccEmail, String user) {
        if (mailService.isEnabled()) {
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), validatedCc(ccEmail), null,
                    Email.DOWNLOAD_FOLDER_CREATION_FAILED_SUBJECT,
                    String.format(Email.DOWNLOAD_FOLDER_CREATION_FAILED_BODY,
                            DOWNLOAD_FOLDER_PATH, formatNow(), sanitizeForEmail(user),
                            sanitizeForEmail(filename), sanitizeForEmail(url)));
        }
    }
}
