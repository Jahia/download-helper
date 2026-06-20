import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
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
import {PROTOCOL_PREFIXES, extractProtocol, stripProtocol, extractFilename} from './formHelpers';

const FileRow = ({file, isDeleting, actionLabel, onDelete}) => (
    <tr className={styles.downloadHelper_file_row}>
        <td className={styles.downloadHelper_file_name}>{file.name}</td>
        <td className={styles.downloadHelper_file_meta}>{file.size}</td>
        <td className={styles.downloadHelper_file_meta}>{file.lastModified}</td>
        <td className={styles.downloadHelper_file_actions}>
            <Tooltip label={actionLabel}>
                <button
                    type="button"
                    className={styles.downloadHelper_icon_btn}
                    aria-label={actionLabel}
                    disabled={isDeleting}
                    onClick={event => onDelete(file.name, event.currentTarget)}
                >
                    <Delete aria-hidden="true" focusable="false"/>
                </button>
            </Tooltip>
        </td>
    </tr>
);

FileRow.propTypes = {
    file: PropTypes.shape({
        name: PropTypes.string.isRequired,
        size: PropTypes.node,
        lastModified: PropTypes.node
    }).isRequired,
    isDeleting: PropTypes.bool,
    actionLabel: PropTypes.string.isRequired,
    onDelete: PropTypes.func.isRequired
};

// Persistent live regions + visible alerts for trigger success/error.
const StatusAlerts = ({t, triggerStatus, visibleAlertRef}) => (
    <>
        {/* Persistent live regions — always present so AT registers them before content appears */}
        <output
            aria-atomic="true"
            aria-live="polite"
            className={styles.downloadHelper_sr_only}
        >
            {triggerStatus === 'success' ? t('downloadHelper.success.started') : ''}
        </output>
        <div
            aria-atomic="true"
            aria-live="assertive"
            className={styles.downloadHelper_sr_only}
            role="alert"
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
    </>
);

StatusAlerts.propTypes = {
    t: PropTypes.func.isRequired,
    triggerStatus: PropTypes.oneOf(['success', 'error']),
    visibleAlertRef: PropTypes.shape({current: PropTypes.any}).isRequired
};

// A required text field with an inline, screen-reader-linked validation error.
const ValidatedField = ({t, id, errorId, label, placeholder, value, hasError, errorKey, onChange}) => (
    <Field id={id} label={label}>
        <Input
            required
            id={id}
            value={value}
            aria-required="true"
            aria-invalid={hasError ? 'true' : undefined}
            aria-describedby={hasError ? errorId : undefined}
            placeholder={placeholder}
            onChange={onChange}
        />
        <span
            id={errorId}
            className={styles.downloadHelper_fieldError}
            hidden={!hasError}
        >
            {hasError ? t(errorKey) : ''}
        </span>
    </Field>
);

ValidatedField.propTypes = {
    t: PropTypes.func.isRequired,
    id: PropTypes.string.isRequired,
    errorId: PropTypes.string.isRequired,
    label: PropTypes.node.isRequired,
    placeholder: PropTypes.string,
    value: PropTypes.string.isRequired,
    hasError: PropTypes.bool.isRequired,
    errorKey: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};

const DownloadForm = ({t, info, formState, isSubmitted, isTriggering, onSubmit, onProtocolChange, onUrlChange, onFilenameChange, onLoginChange, onPasswordChange, onEmailChange}) => {
    const {protocol, url, filename, login, password, email} = formState;
    return (
        <form
            noValidate
            className={styles.downloadHelper_form}
            onSubmit={onSubmit}
        >
            <Field id="dh-protocol" label={t('label.protocol')}>
                <select
                    id="dh-protocol"
                    className={styles.downloadHelper_select}
                    value={protocol}
                    onChange={onProtocolChange}
                >
                    <option value="https">https://</option>
                    <option value="ftp">ftp://</option>
                </select>
                <span className={styles.downloadHelper_hint}>{t('downloadHelper.protocolHint')}</span>
            </Field>

            <ValidatedField
                t={t}
                id="dh-url"
                errorId="dh-url-error"
                label={t('label.url')}
                placeholder="example.com/path/to/file"
                value={url}
                hasError={isSubmitted && !url}
                errorKey="downloadHelper.errors.url.required"
                onChange={onUrlChange}
            />

            <ValidatedField
                t={t}
                id="dh-filename"
                errorId="dh-filename-error"
                label={t('label.filename')}
                placeholder="file.zip"
                value={filename}
                hasError={isSubmitted && !filename}
                errorKey="downloadHelper.errors.filename.required"
                onChange={onFilenameChange}
            />

            <Field id="dh-login" label={t('label.login')}>
                <Input
                    id="dh-login"
                    value={login}
                    autoComplete="username"
                    onChange={onLoginChange}
                />
            </Field>

            <Field id="dh-password" label={t('label.password')}>
                <Input
                    id="dh-password"
                    type="password"
                    value={password}
                    autoComplete="current-password"
                    onChange={onPasswordChange}
                />
            </Field>

            <Field
                id="dh-email"
                label={t('label.email')}
                hint={info.isMailActivated ? undefined : t('downloadHelper.errors.mail.disabled')}
            >
                <Input
                    id="dh-email"
                    value={email}
                    isDisabled={!info.isMailActivated}
                    autoComplete="email"
                    aria-describedby={info.isMailActivated ? undefined : 'dh-email-hint'}
                    placeholder="admin@example.com"
                    onChange={onEmailChange}
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
                    label={isTriggering ? t('label.triggering') : t('label.trigger')}
                    variant="primary"
                    isDisabled={isTriggering || !url || !filename}
                />
            </div>
        </form>
    );
};

DownloadForm.propTypes = {
    t: PropTypes.func.isRequired,
    info: PropTypes.shape({isMailActivated: PropTypes.bool}).isRequired,
    formState: PropTypes.shape({
        protocol: PropTypes.string.isRequired,
        url: PropTypes.string.isRequired,
        filename: PropTypes.string.isRequired,
        login: PropTypes.string.isRequired,
        password: PropTypes.string.isRequired,
        email: PropTypes.string.isRequired
    }).isRequired,
    isSubmitted: PropTypes.bool.isRequired,
    isTriggering: PropTypes.bool.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onProtocolChange: PropTypes.func.isRequired,
    onUrlChange: PropTypes.func.isRequired,
    onFilenameChange: PropTypes.func.isRequired,
    onLoginChange: PropTypes.func.isRequired,
    onPasswordChange: PropTypes.func.isRequired,
    onEmailChange: PropTypes.func.isRequired
};

const FilesTable = ({t, files, deletingName, onDelete}) => (
    <table
        aria-labelledby="dh-files-heading"
        className={styles.downloadHelper_files_table}
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
            {files.map(file => {
                const isDeleting = deletingName === file.name;
                const actionLabel = isDeleting ?
                    `${t('label.deleting')} ${file.name}` :
                    `${t('label.delete')} ${file.name}`;
                return (
                    <FileRow
                        key={file.name}
                        file={file}
                        isDeleting={isDeleting}
                        actionLabel={actionLabel}
                        onDelete={onDelete}
                    />
                );
            })}
        </tbody>
    </table>
);

FilesTable.propTypes = {
    t: PropTypes.func.isRequired,
    files: PropTypes.arrayOf(PropTypes.shape({name: PropTypes.string.isRequired})).isRequired,
    deletingName: PropTypes.string,
    onDelete: PropTypes.func.isRequired
};

const FilesSection = ({t, refreshAreaRef, isFilesLoading, filesError, hasFiles, isEmpty, files, deletingName, onRefresh, onDelete}) => (
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
                onClick={onRefresh}
            />
        </div>

        <output
            aria-label={t('label.filesStatus')}
            aria-live="polite"
            className={isFilesLoading ? styles.downloadHelper_loading : undefined}
        >
            {isFilesLoading ? t('label.loading') : ''}
        </output>

        {filesError && (
            <div role="alert" className={styles.downloadHelper_error}>
                {t('downloadHelper.errors.files.failed')}
            </div>
        )}

        {hasFiles && (
            <FilesTable
                t={t}
                files={files}
                deletingName={deletingName}
                onDelete={onDelete}
            />
        )}

        {isEmpty && (
            <div className={styles.downloadHelper_files_empty}>{t('files.empty')}</div>
        )}
    </div>
);

FilesSection.propTypes = {
    t: PropTypes.func.isRequired,
    refreshAreaRef: PropTypes.shape({current: PropTypes.any}).isRequired,
    isFilesLoading: PropTypes.bool,
    filesError: PropTypes.object,
    hasFiles: PropTypes.bool,
    isEmpty: PropTypes.bool,
    files: PropTypes.arrayOf(PropTypes.shape({name: PropTypes.string.isRequired})),
    deletingName: PropTypes.string,
    onRefresh: PropTypes.func.isRequired,
    onDelete: PropTypes.func.isRequired
};

const DeleteConfirmDialog = ({t, dialogRef, pendingName, isDeleting, onConfirm, onCancel, onClose}) => (
    <dialog
        ref={dialogRef}
        role="alertdialog"
        aria-labelledby="dh-delete-dialog-title"
        aria-describedby="dh-delete-dialog-desc"
        className={styles.downloadHelper_dialog}
        onClose={onClose}
    >
        <h2 id="dh-delete-dialog-title">{t('downloadHelper.deleteDialog.title')}</h2>
        <p id="dh-delete-dialog-desc">{t('downloadHelper.deleteDialog.body', {name: pendingName})}</p>
        <div className={styles.downloadHelper_dialogActions}>
            <Button
                type="button"
                label={t('downloadHelper.deleteDialog.cancel')}
                isDisabled={isDeleting}
                onClick={onCancel}
            />
            <Button
                type="button"
                label={t('downloadHelper.deleteDialog.confirm')}
                variant="danger"
                isDisabled={isDeleting}
                onClick={onConfirm}
            />
        </div>
    </dialog>
);

DeleteConfirmDialog.propTypes = {
    t: PropTypes.func.isRequired,
    dialogRef: PropTypes.shape({current: PropTypes.any}).isRequired,
    pendingName: PropTypes.string,
    isDeleting: PropTypes.bool,
    onConfirm: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};

const NotProcessingServer = ({t}) => (
    <section aria-labelledby="dh-page-heading" className={styles.downloadHelper_container}>
        <div className={styles.downloadHelper_page_header}>
            <h2 id="dh-page-heading">{t('downloadHelper.settings')}</h2>
        </div>
        <Typography>{t('downloadHelper.notProcessingServer')}</Typography>
    </section>
);

NotProcessingServer.propTypes = {
    t: PropTypes.func.isRequired
};

export const DownloadHelperAdmin = () => {
    const {t} = useTranslation('download-helper');

    const [protocol, setProtocol] = useState('https');
    const [url, setUrl] = useState('');
    const [filename, setFilename] = useState('');
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const [triggerStatus, setTriggerStatus] = useState(null);
    // Bumped on every resolved trigger so the focus effect re-fires even on two identical outcomes.
    const [statusSeq, setStatusSeq] = useState(0);
    const [submitted, setSubmitted] = useState(false);
    const [deletingName, setDeletingName] = useState(null);
    const [pendingDeleteName, setPendingDeleteName] = useState(null);
    const filenameManuallySet = useRef(false);
    const visibleAlertRef = useRef(null);
    const refreshAreaRef = useRef(null);
    const deleteDialogRef = useRef(null);
    const deleteTriggerRef = useRef(null);

    // Restore the previous document title on unmount so navigating away does not leave it stale.
    useEffect(() => {
        const previousTitle = document.title;
        document.title = t('downloadHelper.settings');
        return () => {
            document.title = previousTitle;
        };
    }, [t]);

    // Move focus to the freshly committed status/error alert (no magic-number setTimeout, no leak).
    useEffect(() => {
        if (triggerStatus) {
            visibleAlertRef.current?.focus();
        }
    }, [triggerStatus, statusSeq]);

    // Open the confirmation dialog whenever a file is staged for deletion.
    useEffect(() => {
        if (pendingDeleteName !== null) {
            deleteDialogRef.current?.showModal();
        }
    }, [pendingDeleteName]);

    const {data, loading, error} = useQuery(GET_DOWNLOAD_HELPER_INFO, {fetchPolicy: 'network-only'});

    const {
        data: filesData,
        loading: filesLoading,
        error: filesError,
        refetch: refetchFiles
    } = useQuery(GET_DOWNLOAD_HELPER_FILES, {fetchPolicy: 'network-only'});

    const [triggerDownload, {loading: triggering}] = useMutation(TRIGGER_DOWNLOAD);

    const [deleteFile] = useMutation(DELETE_DOWNLOADED_FILE, {
        refetchQueries: [{query: GET_DOWNLOAD_HELPER_FILES}],
        onCompleted: () => {
            setDeletingName(null);
            const firstFocusable = refreshAreaRef.current?.querySelector('button:not(:disabled)');
            firstFocusable?.focus();
        },
        onError: () => setDeletingName(null)
    });

    const resetForm = () => {
        setUrl('');
        setFilename('');
        setLogin('');
        setPassword('');
        setEmail('');
        setSubmitted(false);
        filenameManuallySet.current = false;
    };

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
            if (result.data?.downloadHelperTrigger === true) {
                setTriggerStatus('success');
                resetForm();
            } else {
                setTriggerStatus('error');
            }
        } catch (err) {
            console.error('Failed to trigger download:', err);
            setTriggerStatus('error');
        }

        setStatusSeq(seq => seq + 1);
    };

    const handleFormSubmit = e => {
        e.preventDefault();
        handleSubmit();
    };

    const handleUrlChange = e => {
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
    };

    const handleFilenameChange = e => {
        filenameManuallySet.current = true;
        setFilename(e.target.value);
    };

    // Stage the file for deletion and remember the triggering button for focus return.
    const handleDelete = (name, triggerEl) => {
        deleteTriggerRef.current = triggerEl ?? null;
        setPendingDeleteName(name);
    };

    const handleDeleteConfirm = () => {
        const name = pendingDeleteName;
        deleteDialogRef.current?.close();
        if (!name) {
            return;
        }

        setDeletingName(name);
        deleteFile({variables: {filename: name}});
    };

    const handleDeleteCancel = () => {
        deleteDialogRef.current?.close();
    };

    // Reset staged state and return focus to the delete button that opened the dialog.
    const handleDeleteDialogClose = () => {
        setPendingDeleteName(null);
        deleteTriggerRef.current?.focus();
        deleteTriggerRef.current = null;
    };

    if (loading) {
        return (
            <output className={styles.downloadHelper_loading}>
                {t('label.loading')}
            </output>
        );
    }

    if (error) {
        return (
            <div role="alert" className={styles.downloadHelper_error}>
                {t('downloadHelper.errors.load.failed')}: {error.message}
            </div>
        );
    }

    const info = data?.downloadHelperInfo;

    if (!info || !info.isProcessingServer) {
        return <NotProcessingServer t={t}/>;
    }

    const files = filesData?.downloadHelperFiles;
    const hasFiles = !filesLoading && files && files.length > 0;
    const showEmpty = !filesLoading && !filesError && (!files || files.length === 0);

    return (
        <section aria-labelledby="dh-page-heading" className={styles.downloadHelper_container}>
            <div className={styles.downloadHelper_page_header}>
                <h2 id="dh-page-heading">{t('downloadHelper.settings')}</h2>
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
                <div role="alert" className={`${styles.downloadHelper_alert} ${styles['downloadHelper_alert--warning']}`}>
                    {t('downloadHelper.errors.mail.disabled')}
                </div>
            )}

            <StatusAlerts t={t} triggerStatus={triggerStatus} visibleAlertRef={visibleAlertRef}/>

            <DownloadForm
                t={t}
                info={info}
                formState={{protocol, url, filename, login, password, email}}
                isSubmitted={submitted}
                isTriggering={triggering}
                onSubmit={handleFormSubmit}
                onProtocolChange={e => setProtocol(e.target.value)}
                onUrlChange={handleUrlChange}
                onFilenameChange={handleFilenameChange}
                onLoginChange={e => setLogin(e.target.value)}
                onPasswordChange={e => setPassword(e.target.value)}
                onEmailChange={e => setEmail(e.target.value)}
            />

            <FilesSection
                t={t}
                refreshAreaRef={refreshAreaRef}
                isFilesLoading={filesLoading}
                filesError={filesError}
                hasFiles={hasFiles}
                isEmpty={showEmpty}
                files={files}
                deletingName={deletingName}
                onRefresh={() => refetchFiles()}
                onDelete={handleDelete}
            />

            <DeleteConfirmDialog
                t={t}
                dialogRef={deleteDialogRef}
                pendingName={pendingDeleteName}
                isDeleting={deletingName !== null}
                onConfirm={handleDeleteConfirm}
                onCancel={handleDeleteCancel}
                onClose={handleDeleteDialogClose}
            />
        </section>
    );
};

export default DownloadHelperAdmin;
