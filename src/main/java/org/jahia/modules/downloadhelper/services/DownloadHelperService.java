package org.jahia.modules.downloadhelper.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.util.Base64;
import org.apache.hc.core5.http.HttpStatus;
import org.jahia.modules.downloadhelper.constants.Email;
import org.jahia.services.mail.MailService;
import org.jahia.services.notification.HttpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DownloadHelperService {

    public static final String DOWNLOAD_FOLDER_PATH = "/tmp";
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperService.class);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss z");
    private MailService mailService;
    private HttpClientService httpClientService;

    private static class LazyHolder {

        private static final DownloadHelperService INSTANCE = new DownloadHelperService();
    }

    public static DownloadHelperService getInstance() {
        return DownloadHelperService.LazyHolder.INSTANCE;
    }

    public void download(String protocol, String url, String login, String password, String filename, String ccEmail,
            String ip, String user) throws IOException {
        String completeUrl = "unknown";
        InputStream inputStream = null;
        final File targetFile = new File(DOWNLOAD_FOLDER_PATH, FilenameUtils.getName(filename));
        boolean result = true;
        try {
            if ("https".equals(protocol)) {
                completeUrl = "https://" + url;
                sendEmail(url, filename, ccEmail, ip, user, Email.DOWNLOAD_ASKED_SUBJECT);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Download of %s to %s asked by %s from %s", completeUrl, filename, user, ip));
                }
                final CloseableHttpClient httpClient = httpClientService.getHttpClient(completeUrl);
                final HttpGet httpGet = new HttpGet(completeUrl);
                if (login != null && !login.isEmpty() && password != null && !password.isEmpty()) {
                    httpGet.addHeader("Authorization", "Basic " + new String(Base64.encodeBase64((login + ":" + password).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                }

                httpGet.addHeader(org.apache.http.HttpHeaders.USER_AGENT, "Jahia - Download Helper");
                try ( CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                    final HttpEntity entity = httpResponse.getEntity();
                    final int statusCode = httpResponse.getCode();
                    if (entity != null && HttpStatus.SC_OK == statusCode) {
                        inputStream = entity.getContent();
                        Files.copy(
                                inputStream,
                                targetFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        LOGGER.info(String.format("Download of %s to %s asked by %s from %s has failed", completeUrl, filename, user, ip));
                        result = false;
                    }
                }

            } else if ("ftp".equals(protocol)) {
                completeUrl = String.format("ftp://%s:XXXXX@%s", login, url);
                sendEmail(url, filename, ccEmail, ip, user, Email.DOWNLOAD_ASKED_SUBJECT);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Download of %s to %s asked by %s from %s", completeUrl, filename, user, ip));
                }
                final String encodedLogin = URLEncoder.encode(login, StandardCharsets.UTF_8.toString());
                final String ftpUrl = String.format("ftp://%s:%s@%s", encodedLogin, password, url);
                final URLConnection urlConnection = new URL(ftpUrl).openConnection();
                inputStream = urlConnection.getInputStream();
                Files.copy(
                        inputStream,
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                result = false;
                throw new UnsupportedOperationException("Only https or FTP are allowed");
            }

        } catch (Exception ex) {
            LOGGER.error(String.format("Download of %s to %s asked by %s from %s has failed", completeUrl, filename, user, ip), ex);
            result = false;
        } finally {
            if (result) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Download of %s to %s asked by %s from %s is complete", completeUrl, filename, user, ip));
                }
                sendEmail(url, filename, ccEmail, ip, user, Email.DOWNLOAD_COMPLETED_SUBJECT);
            } else {
                sendEmail(url, filename, ccEmail, ip, user, Email.DOWNLOAD_FAILED_SUBJECT);
            }
            IOUtils.close(inputStream);
        }
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    public void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    private void sendEmail(String url, String filename, String ccEmail, String ip, String user, String subject) {
        if (mailService.isEnabled()) {

            final Date loginDate = new Date();
            final String sender = mailService.defaultSender();
            final String recipient = mailService.defaultRecipient();

            mailService.sendMessage(sender, recipient, ccEmail, null, subject,
                    String.format(Email.DOWNLOAD_BODY, subject, ip, dateFormat.format(loginDate), user, filename, url));
        }
    }
}
