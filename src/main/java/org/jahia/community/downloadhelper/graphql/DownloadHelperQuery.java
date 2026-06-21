package org.jahia.community.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.downloadhelper.constants.DownloadHelperConstants;
import org.jahia.community.downloadhelper.services.DownloadHelperService;
import org.jahia.community.downloadhelper.util.FileSizeUtils;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.mail.MailService;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@GraphQLName("DownloadHelperQuery")
@GraphQLDescription("Download Helper queries")
public class DownloadHelperQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperQuery.class);

    @GraphQLField
    @GraphQLName("info")
    @GraphQLDescription("Returns server information for the download helper admin panel")
    @GraphQLRequiresPermission(DownloadHelperConstants.PERMISSION)
    public GqlServerInfo getDownloadHelperInfo() {
        final boolean isProcessingServer = SettingsBean.getInstance().isProcessingServer();
        // A read must not mutate the filesystem: reflect the current state rather than creating the folder.
        final File downloadFolder = new File(DownloadHelperService.DOWNLOAD_FOLDER_PATH);
        String availableSpace = "0";
        if (downloadFolder.exists()) {
            try {
                final long spaceBytes = Files.getFileStore(
                        Paths.get(DownloadHelperService.DOWNLOAD_FOLDER_PATH)).getUsableSpace();
                if (spaceBytes > 0) {
                    availableSpace = FileSizeUtils.format(spaceBytes);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not determine available disk space", e);
            }
        }

        final MailService mailService = BundleUtils.getOsgiService(MailService.class, null);
        final boolean isMailActivated = mailService != null && mailService.getSettings() != null
                && mailService.getSettings().isServiceActivated();

        return new GqlServerInfo(isProcessingServer, availableSpace, DownloadHelperService.DOWNLOAD_FOLDER_PATH, isMailActivated);
    }

    @GraphQLField
    @GraphQLName("files")
    @GraphQLDescription("Lists files present in the download folder, sorted by last modified date descending")
    @GraphQLRequiresPermission(DownloadHelperConstants.PERMISSION)
    public List<GqlDownloadedFile> getDownloadHelperFiles() {
        final File folder = new File(DownloadHelperService.DOWNLOAD_FOLDER_PATH);
        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }

        final File[] files = folder.listFiles(File::isFile);
        if (files == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(f -> new GqlDownloadedFile(f.getName(), f.length(), f.lastModified()))
                .collect(Collectors.toUnmodifiableList());
    }
}
