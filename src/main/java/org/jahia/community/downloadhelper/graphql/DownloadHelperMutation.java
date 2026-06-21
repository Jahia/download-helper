package org.jahia.community.downloadhelper.graphql;

import graphql.annotations.annotationTypes.*;
import org.jahia.community.downloadhelper.constants.DownloadHelperConstants;
import org.jahia.community.downloadhelper.services.DownloadHelperService;
import org.jahia.community.downloadhelper.util.DownloadPaths;
import org.jahia.community.downloadhelper.util.UrlSecurityUtils;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@GraphQLName("DownloadHelperMutation")
@GraphQLDescription("Download Helper mutations")
public class DownloadHelperMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperMutation.class);

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @GraphQLField
    @GraphQLName("trigger")
    @GraphQLDescription("Triggers an asynchronous file download on the server")
    @GraphQLRequiresPermission(DownloadHelperConstants.PERMISSION)
    public Boolean triggerDownload(
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

        // Execution is delegated to the service-owned, bundle-lifecycle-tied executor rather than a
        // raw thread, so downloads are shut down cleanly when the bundle is deactivated.
        service.submitDownload(protocol, url, login, password, filename, email, currentUser);

        return Boolean.TRUE;
    }

    @GraphQLField
    @GraphQLName("deleteFile")
    @GraphQLDescription("Deletes a file from the download folder")
    @GraphQLRequiresPermission("adminDownloadHelper")
    public Boolean deleteFile(
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
