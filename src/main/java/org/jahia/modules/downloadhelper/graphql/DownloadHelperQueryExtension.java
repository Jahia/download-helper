package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.apache.commons.io.FileSystemUtils;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.mail.MailService;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("DownloadHelperQueries")
@GraphQLDescription("Download Helper queries")
public class DownloadHelperQueryExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperQueryExtension.class);
    private static final String[] UNITS = {"KiB", "MiB", "GiB", "TiB"};
    private static final int KILO_CONSTANT = 1024;

    private DownloadHelperQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("downloadHelperInfo")
    @GraphQLDescription("Returns server information for the download helper admin panel")
    @GraphQLRequiresPermission("adminSystemInfos")
    public static GqlServerInfo getDownloadHelperInfo() {
        final boolean isProcessingServer = SettingsBean.getInstance().isProcessingServer();
        final File downloadFolder = new File(DownloadHelperService.DOWNLOAD_FOLDER_PATH);
        if (!downloadFolder.exists()) {
            if (downloadFolder.mkdirs()) {
                LOGGER.info("Created download folder: {}", DownloadHelperService.DOWNLOAD_FOLDER_PATH);
            } else {
                LOGGER.warn("Could not create download folder: {}", DownloadHelperService.DOWNLOAD_FOLDER_PATH);
            }
        }
        String availableSpace = "0";
        if (downloadFolder.exists()) {
            try {
                final long spaceKb = FileSystemUtils.freeSpaceKb(DownloadHelperService.DOWNLOAD_FOLDER_PATH);
                if (spaceKb > 0) {
                    final int digitGroups = (int) (Math.log10(spaceKb) / Math.log10(KILO_CONSTANT));
                    availableSpace = new DecimalFormat("#,##0.#")
                            .format(spaceKb / Math.pow(KILO_CONSTANT, digitGroups))
                            + " " + UNITS[digitGroups];
                }
            } catch (IOException e) {
                LOGGER.warn("Could not determine available disk space", e);
            }
        }

        final MailService mailService = (MailService) SpringContextSingleton.getBean("MailService");
        final boolean isMailActivated = mailService != null && mailService.getSettings() != null
                && mailService.getSettings().isServiceActivated();

        return new GqlServerInfo(isProcessingServer, availableSpace, DownloadHelperService.DOWNLOAD_FOLDER_PATH, isMailActivated);
    }

    @GraphQLField
    @GraphQLName("downloadHelperFiles")
    @GraphQLDescription("Lists files present in the download folder, sorted by last modified date descending")
    @GraphQLRequiresPermission("adminSystemInfos")
    public static List<GqlDownloadedFile> getDownloadHelperFiles() {
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
                .collect(Collectors.toList());
    }
}
