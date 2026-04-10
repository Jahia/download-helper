import React, {useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Field, Input, Typography} from '@jahia/moonstone';
import styles from './DownloadHelper.scss';
import {GET_DOWNLOAD_HELPER_INFO, TRIGGER_DOWNLOAD} from './DownloadHelper.gql';

export function DownloadHelperAdmin() {
    const {t} = useTranslation('download-helper');

    const [protocol, setProtocol] = useState('https');
    const [url, setUrl] = useState('');
    const [filename, setFilename] = useState('');
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const [triggerStatus, setTriggerStatus] = useState(null); // null | 'success' | 'error'

    const {data, loading, error} = useQuery(GET_DOWNLOAD_HELPER_INFO, {fetchPolicy: 'network-only'});

    const [triggerDownload, {loading: triggering}] = useMutation(TRIGGER_DOWNLOAD);

    const handleSubmit = async () => {
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
    };

    if (loading) {
        return <div className={styles.downloadHelper_loading}>{t('label.loading')}</div>;
    }

    if (error) {
        return (
            <div className={styles.downloadHelper_error}>
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

            {triggerStatus === 'success' && (
                <div className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--success']}`}>
                    {t('downloadHelper.success.started')}
                </div>
            )}
            {triggerStatus === 'error' && (
                <div className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--error']}`}>
                    {t('downloadHelper.errors.trigger.failed')}
                </div>
            )}

            <div className={styles.downloadHelper_form}>
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
                        onChange={e => setUrl(e.target.value)}
                        placeholder="example.com/path/to/file"
                    />
                </Field>

                <Field label={t('label.filename')} id="dh-filename">
                    <Input
                        id="dh-filename"
                        value={filename}
                        onChange={e => setFilename(e.target.value)}
                        placeholder="file.zip"
                    />
                </Field>

                <Field label={t('label.login')} id="dh-login">
                    <Input
                        id="dh-login"
                        value={login}
                        onChange={e => setLogin(e.target.value)}
                    />
                </Field>

                <Field label={t('label.password')} id="dh-password">
                    <Input
                        id="dh-password"
                        type="password"
                        value={password}
                        onChange={e => setPassword(e.target.value)}
                    />
                </Field>

                <Field label={t('label.email')} id="dh-email">
                    <Input
                        id="dh-email"
                        value={email}
                        onChange={e => setEmail(e.target.value)}
                        placeholder="admin@example.com"
                    />
                </Field>

                <div className={styles.downloadHelper_actions}>
                    <Button
                        label={triggering ? t('label.triggering') : t('label.trigger')}
                        variant="primary"
                        isDisabled={triggering || !url || !filename}
                        onClick={handleSubmit}
                    />
                </div>
            </div>
        </div>
    );
}

export default DownloadHelperAdmin;
