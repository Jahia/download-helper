package org.jahia.modules.downloadhelper.services;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.util.Base64;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.jahia.modules.downloadhelper.constants.Email;
import org.jahia.services.mail.MailService;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component(service = DownloadHelperService.class)
public class DownloadHelperService {

    public static final String DOWNLOAD_FOLDER_PATH = "/tmp/jahia-download-helper";
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperService.class);
    private static final int KILO_CONSTANT = 1024;
    private static final String MSG_COULD_NOT_CREATE_FOLDER = "Could not create download folder: {}";
    private static final String DATE_PATTERN = "yyyy/MM/dd 'at' HH:mm:ss z";

    /**
     * Cloud metadata / well-known SSRF target hostnames that must never be reachable
     * even from an admin-triggered download.
     */
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "metadata.google.internal",
            "metadata.goog",
            "metadata"));

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
        if (bytes <= 0) {
            return "0 B";
        }

        final String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        final int digitGroups = (int) (Math.log10(bytes) / Math.log10(KILO_CONSTANT));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(KILO_CONSTANT, digitGroups))
                + " " + units[digitGroups];
    }

    /**
     * Strips CR/LF from a string so attacker-controlled input cannot forge log lines.
     */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }

    /**
     * Rejects URLs that resolve to loopback, link-local, site-local, any-local, multicast addresses
     * or to well-known cloud-metadata hostnames. Defense-in-depth against SSRF even though the
     * caller already requires the {@code adminSystemInfos} permission.
     */
    private static void assertSafeHost(String host) throws IOException {
        if (host == null || host.isEmpty()) {
            throw new IOException("Empty host is not allowed");
        }
        final String lowerHost = host.toLowerCase();
        if (BLOCKED_HOSTS.contains(lowerHost)) {
            throw new IOException("Host is blocked: " + lowerHost);
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Unknown host: " + lowerHost, e);
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("Host resolves to a non-routable / private address: " + lowerHost);
            }
            // 169.254.169.254 (AWS / Azure / OpenStack metadata) is link-local and already blocked above,
            // but also reject the canonical address explicitly for clarity.
            if ("169.254.169.254".equals(address.getHostAddress())) {
                throw new IOException("Cloud metadata endpoint is blocked: " + lowerHost);
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
        final File downloadFolder = new File(DOWNLOAD_FOLDER_PATH);
        if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
            LOGGER.warn(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
        }
    }

    public void download(String protocol, String url, String login, String password,
            String filename, String ccEmail, String user) throws IOException {
        final File downloadFolder = new File(DOWNLOAD_FOLDER_PATH);
        if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
            LOGGER.error(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
            sendFolderCreationFailedEmail(url, filename, ccEmail, user);
            return;
        }

        final File targetFile = new File(DOWNLOAD_FOLDER_PATH, FilenameUtils.getName(filename));
        boolean result = false;
        try {
            if ("https".equals(protocol)) {
                result = downloadHttps(url, login, password, filename, ccEmail, user, targetFile);
            } else if ("ftp".equals(protocol)) {
                result = downloadFtp(url, login, password, filename, ccEmail, user, targetFile);
            } else {
                throw new UnsupportedOperationException("Only https or FTP are allowed");
            }
        } catch (Exception ex) {
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
        final String completeUrl = "https://" + url;
        assertSafeHost(extractHost(completeUrl));
        sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_ASKED_SUBJECT);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Download of {} to {} asked by {}",
                    sanitizeForLog(completeUrl), sanitizeForLog(filename), sanitizeForLog(user));
        }

        final CloseableHttpClient httpClient = httpClientService.getHttpClient(completeUrl);
        final HttpGet httpGet = new HttpGet(completeUrl);
        if (login != null && !login.isEmpty() && password != null && !password.isEmpty()) {
            httpGet.addHeader("Authorization", "Basic " + new String(
                    Base64.encodeBase64((login + ":" + password).getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));
        }

        httpGet.addHeader(org.apache.http.HttpHeaders.USER_AGENT, "Jahia - Download Helper");
        try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
            final HttpEntity entity = httpResponse.getEntity();
            if (entity != null && HttpStatus.SC_OK == httpResponse.getCode()) {
                if (!hasEnoughSpace(entity.getContentLength(), url, filename, ccEmail, user)) {
                    return false;
                }

                copyWithLimit(entity.getContent(), targetFile, entity.getContentLength());
                return true;
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Download of {} to {} asked by {} has failed",
                        sanitizeForLog(completeUrl), sanitizeForLog(filename), sanitizeForLog(user));
            }
            return false;
        }
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
        if (!hasEnoughSpace(urlConnection.getContentLengthLong(), url, filename, ccEmail, user)) {
            return false;
        }

        copyWithLimit(urlConnection.getInputStream(), targetFile, urlConnection.getContentLengthLong());
        return true;
    }

    /**
     * Copies the stream to the target file. When the advertised content length is non-positive
     * (e.g. chunked transfer encoding) the copy is capped at the currently available disk space
     * to mitigate disk-exhaustion DoS.
     */
    private void copyWithLimit(InputStream rawStream, File targetFile, long contentLength) throws IOException {
        try (InputStream inputStream = rawStream) {
            if (contentLength > 0) {
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            final long maxBytes = Files.getFileStore(Paths.get(DOWNLOAD_FOLDER_PATH)).getUsableSpace();
            try (java.io.OutputStream out = Files.newOutputStream(targetFile.toPath())) {
                final byte[] buffer = new byte[8 * KILO_CONSTANT];
                long total = 0;
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    total += read;
                    if (total > maxBytes) {
                        Files.deleteIfExists(targetFile.toPath());
                        throw new IOException("Download exceeds available disk space; aborted");
                    }
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private void sendEmail(String url, String filename, String ccEmail, String user, String subject) {
        if (mailService.isEnabled()) {
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    subject,
                    String.format(Email.DOWNLOAD_BODY, subject, formatNow(), user, filename, url));
        }
    }

    private boolean hasEnoughSpace(long contentLength, String url, String filename,
            String ccEmail, String user) throws IOException {
        if (contentLength <= 0) {
            return true;
        }

        final File downloadFolder = new File(DOWNLOAD_FOLDER_PATH);
        if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
            LOGGER.error(MSG_COULD_NOT_CREATE_FOLDER, DOWNLOAD_FOLDER_PATH);
            sendFolderCreationFailedEmail(url, filename, ccEmail, user);
            return false;
        }

        if (!downloadFolder.exists()) {
            LOGGER.error("Download folder does not exist: {}", DOWNLOAD_FOLDER_PATH);
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
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    Email.DOWNLOAD_INSUFFICIENT_SPACE_SUBJECT,
                    String.format(Email.DOWNLOAD_INSUFFICIENT_SPACE_BODY,
                            DOWNLOAD_FOLDER_PATH, formatSize(contentLength), formatSize(freeBytes),
                            formatNow(), user, filename, url));
        }
    }

    private void sendFolderCreationFailedEmail(String url, String filename, String ccEmail, String user) {
        if (mailService.isEnabled()) {
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    Email.DOWNLOAD_FOLDER_CREATION_FAILED_SUBJECT,
                    String.format(Email.DOWNLOAD_FOLDER_CREATION_FAILED_BODY,
                            DOWNLOAD_FOLDER_PATH, formatNow(), user, filename, url));
        }
    }
}
