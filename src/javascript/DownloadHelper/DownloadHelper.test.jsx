import React from 'react';
import {render, screen, fireEvent, act} from '@testing-library/react';
import {DownloadHelperAdmin} from './DownloadHelper';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: k => k})
}));

// Track the mutation trigger mock so tests can configure its resolved value.
const mockTriggerDownload = jest.fn();
const mockDeleteFile = jest.fn();
const mockRefetchFiles = jest.fn();

jest.mock('@apollo/client', () => ({
    gql: (strings, ...values) => {
        const raw = strings.reduce((acc, s, i) => acc + s + (values[i] || ''), '');
        // Extract operation name from the GQL string so callers can be identified.
        const match = raw.match(/(?:query|mutation)\s+(\w+)/);
        return {kind: 'Document', definitions: [{kind: 'OperationDefinition', name: {value: match ? match[1] : ''}}]};
    },
    useQuery: jest.fn(() => ({})),
    useMutation: jest.fn()
}));

// @jahia/moonstone: return lightweight HTML stand-ins.
jest.mock('@jahia/moonstone', () => ({
    Button: ({label, onClick, isDisabled, type}) => (
        <button
            type={type === 'submit' ? 'submit' : 'button'}
            disabled={isDisabled}
            onClick={onClick}
        >
            {label}
        </button>
    ),
    Field: ({children}) => <div>{children}</div>,
    Input: ({id, value, onChange, isDisabled, placeholder, type}) => (
        <input
            id={id}
            type={type || 'text'}
            value={value}
            disabled={isDisabled}
            placeholder={placeholder}
            onChange={onChange}
        />
    ),
    Typography: ({children}) => <span>{children}</span>,
    Tooltip: ({children}) => <>{children}</>,
    Delete: () => <span>Delete</span>
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const {useQuery, useMutation} = require('@apollo/client');

function setupMocks({infoData, filesData, triggerResult = {data: {downloadHelperTrigger: true}}}) {
    // Identify the query by its parsed operation name so re-renders don't skew a counter.
    useQuery.mockImplementation(query => {
        const opName = query?.definitions?.[0]?.name?.value || '';
        if (opName === 'DownloadHelperInfo') {
            return {data: infoData, loading: false, error: undefined};
        }

        // DownloadHelperFiles (or anything else)
        return {
            data: filesData,
            loading: false,
            error: undefined,
            refetch: mockRefetchFiles
        };
    });

    mockTriggerDownload.mockResolvedValue(triggerResult);
    useMutation.mockImplementation(mutation => {
        const mutationName = mutation?.definitions?.[0]?.name?.value || '';
        if (mutationName === 'TriggerDownload') {
            return [mockTriggerDownload, {loading: false}];
        }

        return [mockDeleteFile, {}];
    });
}

const defaultInfo = {
    downloadHelperInfo: {
        isProcessingServer: true,
        isMailActivated: true,
        availableSpace: '10 GiB',
        downloadFolderPath: '/tmp/jahia-download-helper'
    }
};

const defaultFiles = {downloadHelperFiles: []};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

beforeEach(() => {
    jest.clearAllMocks();
});

describe('DownloadHelperAdmin', () => {
    test('renders mail-disabled warning and disables email field when isMailActivated=false', () => {
        // Arrange
        setupMocks({
            infoData: {
                downloadHelperInfo: {
                    isProcessingServer: true,
                    isMailActivated: false,
                    availableSpace: 'x',
                    downloadFolderPath: '/tmp'
                }
            },
            filesData: defaultFiles
        });

        // Act
        render(<DownloadHelperAdmin/>);

        // Assert — mail disabled warning key is present
        const warnings = screen.getAllByText('downloadHelper.errors.mail.disabled');
        expect(warnings.length).toBeGreaterThan(0);

        // Assert — email input is disabled
        const emailInput = document.getElementById('dh-email');
        expect(emailInput).toBeDisabled();
    });

    test('renders fallback notProcessingServer message and hides form when isProcessingServer=false', () => {
        // Arrange
        setupMocks({
            infoData: {
                downloadHelperInfo: {
                    isProcessingServer: false,
                    isMailActivated: true,
                    availableSpace: '0',
                    downloadFolderPath: '/tmp'
                }
            },
            filesData: defaultFiles
        });

        // Act
        render(<DownloadHelperAdmin/>);

        // Assert — fallback text key is shown
        expect(screen.getByText('downloadHelper.notProcessingServer')).toBeInTheDocument();

        // Assert — the URL input (form) is absent
        expect(document.getElementById('dh-url')).toBeNull();
    });

    test('shows error alert when downloadHelperTrigger returns false on submit', async () => {
        // Arrange
        setupMocks({
            infoData: defaultInfo,
            filesData: defaultFiles,
            triggerResult: {data: {downloadHelperTrigger: false}}
        });

        render(<DownloadHelperAdmin/>);

        // Fill in url and filename so the form validation passes
        const urlInput = document.getElementById('dh-url');
        const filenameInput = document.getElementById('dh-filename');

        await act(async () => {
            fireEvent.change(urlInput, {target: {value: 'example.com/path/file.zip'}});
            fireEvent.change(filenameInput, {target: {value: 'file.zip'}});
        });

        // Act — submit the form and let the async mutation resolve
        await act(async () => {
            const form = urlInput.closest('form');
            fireEvent.submit(form);
        });

        // Assert — error alert with the failure key appears (present in both the
        // visible alert div and the persistent aria-live region).
        const alerts = screen.getAllByText('downloadHelper.errors.trigger.failed');
        expect(alerts.length).toBeGreaterThan(0);
    });
});
