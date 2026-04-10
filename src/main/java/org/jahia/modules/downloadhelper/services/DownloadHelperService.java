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
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component(service = DownloadHelperService.class)
public class DownloadHelperService {

    public static final String DOWNLOAD_FOLDER_PATH = "/tmp/jahia-download-helper";
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperService.class);
    private static final int KILO_CONSTANT = 1024;
    private static final String MSG_COULD_NOT_CREATE_FOLDER = "Could not create download folder: {}";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss z");

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
            LOGGER.error("Download of {} to {} asked by {} has failed", url, filename, user, ex);
        } finally {
            if (result) {
                LOGGER.info("Download of {} to {} asked by {} is complete", url, filename, user);
                sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_COMPLETED_SUBJECT);
            } else {
                sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_FAILED_SUBJECT);
            }
        }
    }

    private boolean downloadHttps(String url, String login, String password, String filename,
            String ccEmail, String user, File targetFile) throws IOException {
        final String completeUrl = "https://" + url;
        sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_ASKED_SUBJECT);
        LOGGER.info("Download of {} to {} asked by {}", completeUrl, filename, user);

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

                try (InputStream inputStream = entity.getContent()) {
                    Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                return true;
            }

            LOGGER.info("Download of {} to {} asked by {} has failed", completeUrl, filename, user);
            return false;
        }
    }

    private boolean downloadFtp(String url, String login, String password, String filename,
            String ccEmail, String user, File targetFile) throws IOException {
        final String completeUrl = String.format("ftp://%s:XXXXX@%s", login, url);
        sendEmail(url, filename, ccEmail, user, Email.DOWNLOAD_ASKED_SUBJECT);
        LOGGER.info("Download of {} to {} asked by {}", completeUrl, filename, user);

        final String encodedLogin = URLEncoder.encode(login, StandardCharsets.UTF_8);
        final String ftpUrl = String.format("ftp://%s:%s@%s", encodedLogin, password, url);
        final URLConnection urlConnection = new URL(ftpUrl).openConnection();
        if (!hasEnoughSpace(urlConnection.getContentLengthLong(), url, filename, ccEmail, user)) {
            return false;
        }

        try (InputStream inputStream = urlConnection.getInputStream()) {
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return true;
    }

    private void sendEmail(String url, String filename, String ccEmail, String user, String subject) {
        if (mailService.isEnabled()) {
            final Date loginDate = new Date();
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    subject,
                    String.format(Email.DOWNLOAD_BODY, subject, dateFormat.format(loginDate), user, filename, url));
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
            final Date loginDate = new Date();
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    Email.DOWNLOAD_INSUFFICIENT_SPACE_SUBJECT,
                    String.format(Email.DOWNLOAD_INSUFFICIENT_SPACE_BODY,
                            DOWNLOAD_FOLDER_PATH, formatSize(contentLength), formatSize(freeBytes),
                            dateFormat.format(loginDate), user, filename, url));
        }
    }

    private void sendFolderCreationFailedEmail(String url, String filename, String ccEmail, String user) {
        if (mailService.isEnabled()) {
            final Date loginDate = new Date();
            mailService.sendMessage(
                    mailService.defaultSender(), mailService.defaultRecipient(), ccEmail, null,
                    Email.DOWNLOAD_FOLDER_CREATION_FAILED_SUBJECT,
                    String.format(Email.DOWNLOAD_FOLDER_CREATION_FAILED_BODY,
                            DOWNLOAD_FOLDER_PATH, dateFormat.format(loginDate), user, filename, url));
        }
    }
}
