import React, {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Delete, Field, Input, Tooltip, Typography} from '@jahia/moonstone';
import styles from './DownloadHelper.scss';
import {
    DELETE_DOWNLOADED_FILE,
    GET_DOWNLOAD_HELPER_FILES,
    GET_DOWNLOAD_HELPER_INFO,
    TRIGGER_DOWNLOAD
} from './DownloadHelper.gql';

const PROTOCOL_PREFIXES = ['https://', 'ftp://'];

const extractProtocol = url => {
    if (url.startsWith('ftp://')) {
        return 'ftp';
    }

    return 'https';
};

const stripProtocol = url => {
    for (const prefix of PROTOCOL_PREFIXES) {
        if (url.startsWith(prefix)) {
            return url.slice(prefix.length);
        }
    }

    return url;
};

const extractFilename = url => {
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

export function DownloadHelperAdmin() {
    const {t} = useTranslation('download-helper');

    useEffect(() => {
        document.title = t('downloadHelper.settings');
    }, [t]);

    const [protocol, setProtocol] = useState('https');
    const [url, setUrl] = useState('');
    const [filename, setFilename] = useState('');
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const [triggerStatus, setTriggerStatus] = useState(null);
    const [submitted, setSubmitted] = useState(false);
    const filenameManuallySet = useRef(false);
    const visibleAlertRef = useRef(null);
    const refreshAreaRef = useRef(null);

    const {data, loading, error} = useQuery(GET_DOWNLOAD_HELPER_INFO, {fetchPolicy: 'network-only'});

    const {data: filesData, loading: filesLoading, refetch: refetchFiles} = useQuery(
        GET_DOWNLOAD_HELPER_FILES, {fetchPolicy: 'network-only'}
    );

    const [triggerDownload, {loading: triggering}] = useMutation(TRIGGER_DOWNLOAD);

    const [deleteFile, {loading: deleting}] = useMutation(DELETE_DOWNLOADED_FILE, {
        refetchQueries: [{query: GET_DOWNLOAD_HELPER_FILES}],
        onCompleted: () => {
            const firstFocusable = refreshAreaRef.current?.querySelector('button:not(:disabled)');
            firstFocusable?.focus();
        }
    });

    const handleSubmit = async () => {
        setSubmitted(true);
        if (!url || !filename) {
            return;
        }

        setTriggerStatus(null);
        try {
            const result = await triggerDownload({
                variables: {
                    protocol,
                    url,
                    filename,
                    login: login || null,
                    password: password || null,
                    email: email || null
                }
            });
            if (result.data && result.data.downloadHelperTrigger) {
                setTriggerStatus('success');
            } else {
                setTriggerStatus('error');
            }
        } catch (err) {
            console.error('Failed to trigger download:', err);
            setTriggerStatus('error');
        }

        setTimeout(() => visibleAlertRef.current?.focus(), 50);
    };

    if (loading) {
        return (
            <div role="status" aria-live="polite" className={styles.downloadHelper_loading}>
                {t('label.loading')}
            </div>
        );
    }

    if (error) {
        return (
            <div role="alert" className={styles.downloadHelper_error}>
                {t('downloadHelper.errors.load.failed')}: {error.message}
            </div>
        );
    }

    const info = data && data.downloadHelperInfo;

    if (!info || !info.isProcessingServer) {
        return (
            <div className={styles.downloadHelper_container}>
                <div className={styles.downloadHelper_page_header}>
                    <h2>{t('downloadHelper.settings')}</h2>
                </div>
                <Typography>{t('downloadHelper.notProcessingServer')}</Typography>
            </div>
        );
    }

    return (
        <div className={styles.downloadHelper_container}>
            <div className={styles.downloadHelper_page_header}>
                <h2>{t('downloadHelper.settings')}</h2>
            </div>

            <div className={styles.downloadHelper_info}>
                <p>
                    {t('downloadHelper.info', {
                        availableSpace: info.availableSpace,
                        downloadFolderPath: info.downloadFolderPath
                    })}
                </p>
            </div>

            {!info.isMailActivated && (
                <div role="region" aria-label={t('downloadHelper.errors.mail.disabled')} className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--warning']}`}>
                    {t('downloadHelper.errors.mail.disabled')}
                </div>
            )}

            {/* Persistent live regions — always present so AT registers them before content appears */}
            <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className={styles.downloadHelper_sr_only}
            >
                {triggerStatus === 'success' ? t('downloadHelper.success.started') : ''}
            </div>
            <div
                role="alert"
                aria-live="assertive"
                aria-atomic="true"
                className={styles.downloadHelper_sr_only}
            >
                {triggerStatus === 'error' ? t('downloadHelper.errors.trigger.failed') : ''}
            </div>

            {triggerStatus === 'success' && (
                <div ref={visibleAlertRef} tabIndex={-1} className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--success']}`}>
                    {t('downloadHelper.success.started')}
                </div>
            )}

            {triggerStatus === 'error' && (
                <div ref={visibleAlertRef} tabIndex={-1} className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--error']}`}>
                    {t('downloadHelper.errors.trigger.failed')}
                </div>
            )}

            <form
                className={styles.downloadHelper_form}
                onSubmit={e => {
                    e.preventDefault();
                    handleSubmit();
                }}
                noValidate
            >
                <Field label={t('label.protocol')} id="dh-protocol">
                    <select
                        id="dh-protocol"
                        className={styles.downloadHelper_select}
                        value={protocol}
                        onChange={e => setProtocol(e.target.value)}
                    >
                        <option value="https">https://</option>
                        <option value="ftp">ftp://</option>
                    </select>
                </Field>

                <Field label={t('label.url')} id="dh-url">
                    <Input
                        id="dh-url"
                        value={url}
                        required
                        aria-required="true"
                        aria-invalid={submitted && !url ? 'true' : undefined}
                        aria-describedby={submitted && !url ? 'dh-url-error' : undefined}
                        onChange={e => {
                            const raw = e.target.value;
                            const hasPrefix = PROTOCOL_PREFIXES.some(p => raw.startsWith(p));
                            const newUrl = hasPrefix ? stripProtocol(raw) : raw;
                            if (hasPrefix) {
                                setProtocol(extractProtocol(raw));
                            }

                            setUrl(newUrl);
                            if (!filenameManuallySet.current) {
                                setFilename(extractFilename(newUrl));
                            }
                        }}
                        placeholder="example.com/path/to/file"
                    />
                    {submitted && !url && (
                        <span id="dh-url-error" className={styles.downloadHelper_fieldError} role="alert">{t('downloadHelper.errors.url.required')}</span>
                    )}
                </Field>

                <Field label={t('label.filename')} id="dh-filename">
                    <Input
                        id="dh-filename"
                        value={filename}
                        required
                        aria-required="true"
                        aria-invalid={submitted && !filename ? 'true' : undefined}
                        aria-describedby={submitted && !filename ? 'dh-filename-error' : undefined}
                        onChange={e => {
                            filenameManuallySet.current = true;
                            setFilename(e.target.value);
                        }}
                        placeholder="file.zip"
                    />
                    {submitted && !filename && (
                        <span id="dh-filename-error" className={styles.downloadHelper_fieldError} role="alert">{t('downloadHelper.errors.filename.required')}</span>
                    )}
                </Field>

                <Field label={t('label.login')} id="dh-login">
                    <Input
                        id="dh-login"
                        value={login}
                        autoComplete="username"
                        onChange={e => setLogin(e.target.value)}
                    />
                </Field>

                <Field label={t('label.password')} id="dh-password">
                    <Input
                        id="dh-password"
                        type="password"
                        value={password}
                        autoComplete="current-password"
                        onChange={e => setPassword(e.target.value)}
                    />
                </Field>

                <Field
                    label={t('label.email')}
                    id="dh-email"
                    hint={!info.isMailActivated ? t('downloadHelper.errors.mail.disabled') : undefined}
                >
                    <Input
                        id="dh-email"
                        value={email}
                        autoComplete="email"
                        aria-describedby={!info.isMailActivated ? 'dh-email-hint' : undefined}
                        onChange={e => setEmail(e.target.value)}
                        placeholder="admin@example.com"
                        isDisabled={!info.isMailActivated}
                    />
                </Field>

                {!info.isMailActivated && (
                    <span id="dh-email-hint" className={styles.downloadHelper_sr_only}>
                        {t('downloadHelper.errors.mail.disabled')}
                    </span>
                )}

                <div className={styles.downloadHelper_actions}>
                    <Button
                        type="submit"
                        label={triggering ? t('label.triggering') : t('label.trigger')}
                        variant="primary"
                        isDisabled={triggering || !url || !filename}
                    />
                </div>
            </form>

            <div ref={refreshAreaRef} className={styles.downloadHelper_section}>
                <div className={styles.downloadHelper_section_header}>
                    <h3 id="dh-files-heading" className={styles.downloadHelper_section_title}>
                        {t('files.title')}
                    </h3>
                    <Button
                        type="button"
                        label={t('label.refresh')}
                        variant="ghost"
                        size="small"
                        onClick={() => refetchFiles()}
                    />
                </div>

                <div role="status" aria-live="polite" className={styles.downloadHelper_loading}>
                    {filesLoading ? t('label.loading') : ''}
                </div>

                {!filesLoading && filesData && filesData.downloadHelperFiles && filesData.downloadHelperFiles.length > 0 ? (
                    <table
                        className={styles.downloadHelper_files_table}
                        aria-labelledby="dh-files-heading"
                    >
                        <thead className={styles.downloadHelper_files_thead}>
                            <tr>
                                <th scope="col">{t('files.name')}</th>
                                <th scope="col">{t('files.size')}</th>
                                <th scope="col">{t('files.lastModified')}</th>
                                <th scope="col">
                                    <span className={styles.downloadHelper_sr_only}>{t('label.actions')}</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {filesData.downloadHelperFiles.map(file => (
                                <tr key={file.name} className={styles.downloadHelper_file_row}>
                                    <td className={styles.downloadHelper_file_name}>{file.name}</td>
                                    <td className={styles.downloadHelper_file_meta}>{file.size}</td>
                                    <td className={styles.downloadHelper_file_meta}>{file.lastModified}</td>
                                    <td className={styles.downloadHelper_file_actions}>
                                        <Tooltip label={`${t('label.delete')} ${file.name}`}>
                                            <button
                                                type="button"
                                                className={styles.downloadHelper_icon_btn}
                                                aria-label={`${t('label.delete')} ${file.name}`}
                                                disabled={deleting}
                                                onClick={() => deleteFile({variables: {filename: file.name}})}
                                            >
                                                <Delete aria-hidden="true" focusable="false"/>
                                            </button>
                                        </Tooltip>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                ) : !filesLoading ? (
                    <div className={styles.downloadHelper_files_empty}>{t('files.empty')}</div>
                ) : null}
            </div>
        </div>
    );
}

export default DownloadHelperAdmin;
