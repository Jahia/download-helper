package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.*;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.modules.downloadhelper.util.DownloadPaths;
import org.jahia.modules.downloadhelper.util.UrlSecurityUtils;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("DownloadHelperMutations")
@GraphQLDescription("Download Helper mutations")
public class DownloadHelperMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperMutationExtension.class);

    private DownloadHelperMutationExtension() {
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @GraphQLField
    @GraphQLName("downloadHelperTrigger")
    @GraphQLDescription("Triggers an asynchronous file download on the server")
    @GraphQLRequiresPermission("adminSystemInfos")
    public static Boolean triggerDownload(
            @GraphQLName("protocol") @GraphQLNonNull final String protocol,
            @GraphQLName("url") @GraphQLNonNull final String url,
            @GraphQLName("filename") @GraphQLNonNull final String filename,
            @GraphQLName("login") final String login,
            @GraphQLName("password") final String password,
            @GraphQLName("email") final String email) {

        if (isBlank(protocol) || isBlank(url) || isBlank(filename)) {
            LOGGER.warn("Rejected download trigger with blank protocol/url/filename");
            return Boolean.FALSE;
        }

        final DownloadHelperService service = BundleUtils.getOsgiService(DownloadHelperService.class, null);
        if (service == null) {
            LOGGER.error("DownloadHelperService is not available");
            return Boolean.FALSE;
        }

        final String currentUser = JCRSessionFactory.getInstance().getCurrentUser().getUserKey();

        new Thread(() -> {
            try {
                service.download(protocol, url, login, password, filename, email, currentUser);
            } catch (IOException e) {
                LOGGER.error("Async download failed for url={} filename={} user={}",
                        UrlSecurityUtils.sanitizeForLog(url),
                        UrlSecurityUtils.sanitizeForLog(filename),
                        UrlSecurityUtils.sanitizeForLog(currentUser), e);
            }
        }).start();

        return Boolean.TRUE;
    }

    @GraphQLField
    @GraphQLName("downloadHelperDeleteFile")
    @GraphQLDescription("Deletes a file from the download folder")
    @GraphQLRequiresPermission("adminSystemInfos")
    public static Boolean deleteFile(
            @GraphQLName("filename") @GraphQLNonNull final String filename) {

        final File file;
        try {
            file = DownloadPaths.resolveContainedFile(DownloadHelperService.DOWNLOAD_FOLDER_PATH, filename);
        } catch (IOException e) {
            LOGGER.warn("Rejected unsafe filename for delete: {}", UrlSecurityUtils.sanitizeForLog(filename));
            return Boolean.FALSE;
        }

        if (!file.exists()) {
            return Boolean.FALSE;
        }

        try {
            Files.delete(file.toPath());
            return Boolean.TRUE;
        } catch (IOException e) {
            LOGGER.warn("Could not delete file: {}", file.getAbsolutePath(), e);
            return Boolean.FALSE;
        }
    }
}
