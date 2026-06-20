export const PROTOCOL_PREFIXES = ['https://', 'ftp://'];

export const extractProtocol = url => (url.startsWith('ftp://') ? 'ftp' : 'https');

export const stripProtocol = url => {
    for (const prefix of PROTOCOL_PREFIXES) {
        if (url.startsWith(prefix)) {
            return url.slice(prefix.length);
        }
    }

    return url;
};

export const extractFilename = url => {
    if (!url) {
        return '';
    }

    try {
        const parsed = new URL(url.includes('://') ? url : 'https://' + url);
        const segments = parsed.pathname.split('/').filter(Boolean);
        return segments[segments.length - 1] || '';
    } catch {
        const segments = url.split('/').filter(Boolean);
        return (segments[segments.length - 1] || '').split('?')[0];
    }
};
