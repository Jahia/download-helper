package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Download Helper queries")
public class DownloadHelperQueryExtension {

    private DownloadHelperQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("downloadHelper")
    @GraphQLDescription("Download Helper query namespace")
    public static DownloadHelperQuery downloadHelper() {
        return new DownloadHelperQuery();
    }
}
