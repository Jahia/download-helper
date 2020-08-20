import {registry} from '@jahia/ui-extender';
import {Person} from '@jahia/moonstone/dist/icons';
import React from 'react';
import {DefaultEntry} from '@jahia/moonstone/dist/icons';

export const registerRoutes = function () {
    registry.add('adminRoute', 'downloadHelper', {
        targets: ['administration-server-systemComponents:10'],
        requiredPermission: 'administrationAccess',
        icon: <DefaultEntry/>,
        label: 'download-helper:settings',
        isSelectable: true,
        iframeUrl: window.contextJsParameters.contextPath + '/cms/adminframe/default/$lang/settings.downloadHelper.html'
    });
};