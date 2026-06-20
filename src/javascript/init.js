import {registry} from '@jahia/ui-extender';
import register from './DownloadHelper/register';
import i18next from 'i18next';

export default function initDownloadHelper() {
    registry.add('callback', 'download-helper', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('download-helper');
            register();
        }
    });
}
