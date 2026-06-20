package org.jahia.modules.downloadhelper.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class DownloadHelperGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
