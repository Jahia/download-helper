package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@GraphQLName("DownloadedFile")
@GraphQLDescription("A file present in the download folder")
public class GqlDownloadedFile {

    private static final int KILO_CONSTANT = 1024;
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
        if (sizeBytes <= 0) {
            return "0 B";
        }

        final String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        final int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(KILO_CONSTANT));
        return new DecimalFormat("#,##0.#").format(sizeBytes / Math.pow(KILO_CONSTANT, digitGroups))
                + " " + units[digitGroups];
    }

    @GraphQLField
    @GraphQLName("lastModified")
    @GraphQLDescription("Last modification date formatted as yyyy-MM-dd HH:mm:ss")
    public String getLastModified() {
        return new SimpleDateFormat(DATE_FORMAT).format(new Date(lastModifiedMs));
    }
}
