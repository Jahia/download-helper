package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("DownloadHelperServerInfo")
@GraphQLDescription("Server information for the download helper")
public class GqlServerInfo {

    private final boolean processingServer;
    private final String availableSpace;
    private final String downloadFolderPath;

    public GqlServerInfo(boolean processingServer, String availableSpace, String downloadFolderPath) {
        this.processingServer = processingServer;
        this.availableSpace = availableSpace;
        this.downloadFolderPath = downloadFolderPath;
    }

    @GraphQLField
    @GraphQLName("isProcessingServer")
    @GraphQLDescription("Whether this is the processing server")
    public boolean isProcessingServer() {
        return processingServer;
    }

    @GraphQLField
    @GraphQLName("availableSpace")
    @GraphQLDescription("Available disk space in the download folder")
    public String getAvailableSpace() {
        return availableSpace;
    }

    @GraphQLField
    @GraphQLName("downloadFolderPath")
    @GraphQLDescription("Path to the download folder")
    public String getDownloadFolderPath() {
        return downloadFolderPath;
    }
}
