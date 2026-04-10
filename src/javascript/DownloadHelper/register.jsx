import {registry} from '@jahia/ui-extender';
import DownloadHelperAdmin from './DownloadHelper';
import React from 'react';

export default () => {
    registry.add('adminRoute', 'downloadHelper', {
        targets: ['administration-server-systemHealth:10'],
        requiredPermission: 'adminSystemInfos',
        label: 'download-helper:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(DownloadHelperAdmin)
    });
};
