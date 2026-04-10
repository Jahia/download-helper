import {registry} from '@jahia/ui-extender';
import register from './DownloadHelper/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'download-helper', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('download-helper', () => {
                console.debug('%c download-helper: i18n namespace loaded', 'color: #463CBA');
            });
            register();
            console.debug('%c download-helper: activation completed', 'color: #463CBA');
        }
    });
}
