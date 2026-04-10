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
    private final boolean mailActivated;

    public GqlServerInfo(boolean processingServer, String availableSpace, String downloadFolderPath, boolean mailActivated) {
        this.processingServer = processingServer;
        this.availableSpace = availableSpace;
        this.downloadFolderPath = downloadFolderPath;
        this.mailActivated = mailActivated;
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

    @GraphQLField
    @GraphQLName("isMailActivated")
    @GraphQLDescription("Whether the mail service is activated (MailSettings.isServiceActivated)")
    public boolean isMailActivated() {
        return mailActivated;
    }
}
