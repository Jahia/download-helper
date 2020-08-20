window.jahia.i18n.loadNamespaces('download-helper');

window.jahia.uiExtender.registry.add('adminRoute', 'downloadHelper', {
    targets: ['administration-server-systemHealth:10'],
    label: 'download-helper:label',
    isSelectable: true,
    iframeUrl: window.contextJsParameters.contextPath + '/cms/adminframe/default/$lang/settings.downloadHelper.html'
});