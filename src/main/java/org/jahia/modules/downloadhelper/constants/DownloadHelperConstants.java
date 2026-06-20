package org.jahia.modules.downloadhelper.constants;

/**
 * Shared, non-instantiable constants for the download-helper module.
 */
public final class DownloadHelperConstants {

    /**
     * Permission required to access every download-helper GraphQL operation.
     * The exact string value is depended upon by RBAC configuration and Cypress tests; do not change it.
     */
    public static final String PERMISSION = "adminDownloadHelper";

    private DownloadHelperConstants() {
    }
}
