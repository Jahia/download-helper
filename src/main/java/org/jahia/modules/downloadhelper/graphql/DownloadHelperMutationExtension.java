package org.jahia.modules.downloadhelper.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("DownloadHelperMutations")
@GraphQLDescription("Download Helper mutations")
public class DownloadHelperMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperMutationExtension.class);

    @GraphQLField
    @GraphQLName("downloadHelperTrigger")
    @GraphQLDescription("Triggers an asynchronous file download on the server")
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
}
