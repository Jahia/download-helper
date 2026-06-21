package org.jahia.community.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.downloadhelper.util.FileSizeUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

@GraphQLName("DownloadedFile")
@GraphQLDescription("A file present in the download folder")
public class GqlDownloadedFile {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final String name;
    private final long sizeBytes;
    private final long lastModifiedMs;

    public GqlDownloadedFile(String name, long sizeBytes, long lastModifiedMs) {
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.lastModifiedMs = lastModifiedMs;
    }

    @GraphQLField
    @GraphQLName("name")
    @GraphQLDescription("File name")
    public String getName() {
        return name;
    }

    @GraphQLField
    @GraphQLName("size")
    @GraphQLDescription("Human-readable file size")
    public String getSize() {
        return FileSizeUtils.format(sizeBytes);
    }

    @GraphQLField
    @GraphQLName("lastModified")
    @GraphQLDescription("Last modification date formatted as yyyy-MM-dd HH:mm:ss")
    public String getLastModified() {
        return new SimpleDateFormat(DATE_FORMAT).format(new Date(lastModifiedMs));
    }
}
