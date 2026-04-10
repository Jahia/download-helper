package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.apache.commons.io.FileSystemUtils;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;

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
    public static GqlServerInfo getDownloadHelperInfo() {
        final boolean isProcessingServer = SettingsBean.getInstance().isProcessingServer();
        String availableSpace = "0";
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

        return new GqlServerInfo(isProcessingServer, availableSpace, DownloadHelperService.DOWNLOAD_FOLDER_PATH);
    }
}
