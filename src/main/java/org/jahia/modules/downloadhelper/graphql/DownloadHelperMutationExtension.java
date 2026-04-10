package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("DownloadHelperMutations")
@GraphQLDescription("Download Helper mutations")
public class DownloadHelperMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperMutationExtension.class);

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

        final DownloadHelperService service = BundleUtils.getOsgiService(DownloadHelperService.class, null);
        if (service == null) {
            LOGGER.error("DownloadHelperService is not available");
            return Boolean.FALSE;
        }

        final String currentUser = JCRSessionFactory.getInstance().getCurrentUser().getUserKey();

        new Thread(() -> {
            try {
                service.download(protocol, url, login, password, filename, email, "unknown", currentUser);
            } catch (IOException e) {
                LOGGER.error("Async download failed for url={} filename={} user={}", url, filename, currentUser, e);
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

        final String safeName = FilenameUtils.getName(filename);
        if (safeName.isEmpty()) {
            LOGGER.warn("Rejected empty or path-only filename: {}", filename);
            return Boolean.FALSE;
        }

        final File file = new File(DownloadHelperService.DOWNLOAD_FOLDER_PATH, safeName);
        try {
            final String canonicalFile = file.getCanonicalPath();
            final String canonicalFolder = new File(DownloadHelperService.DOWNLOAD_FOLDER_PATH).getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalFolder + File.separator)) {
                LOGGER.warn("Path traversal attempt rejected for filename: {}", filename);
                return Boolean.FALSE;
            }
        } catch (IOException e) {
            LOGGER.error("Could not resolve canonical path for filename: {}", filename, e);
            return Boolean.FALSE;
        }

        if (!file.exists()) {
            return Boolean.FALSE;
        }

        final boolean deleted = file.delete();
        if (!deleted) {
            LOGGER.warn("Could not delete file: {}", file.getAbsolutePath());
        }

        return deleted;
    }
}
