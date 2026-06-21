package org.jahia.community.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Download Helper mutations")
public class DownloadHelperMutationExtension {

    private DownloadHelperMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("downloadHelper")
    @GraphQLDescription("Download Helper mutation namespace")
    public static DownloadHelperMutation downloadHelper() {
        return new DownloadHelperMutation();
    }
}
