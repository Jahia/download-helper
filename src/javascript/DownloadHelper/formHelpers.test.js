import {extractFilename, extractProtocol, stripProtocol} from './formHelpers';

describe('extractFilename', () => {
    test('returns last path segment and strips query string', () => {
        // Arrange
        const url = 'example.com/path/file.zip?token=abc';

        // Act
        const result = extractFilename(url);

        // Assert
        expect(result).toBe('file.zip');
    });

    test('returns empty string when url has no path segments', () => {
        // Arrange
        const url = 'example.com';

        // Act
        const result = extractFilename(url);

        // Assert
        // new URL('https://example.com').pathname === '/', split('/').filter(Boolean) === []
        expect(result).toBe('');
    });

    test('returns last segment from ftp URL', () => {
        // Arrange
        const url = 'ftp://files.example.com/data/archive.tar.gz';

        // Act
        const result = extractFilename(url);

        // Assert
        expect(result).toBe('archive.tar.gz');
    });

    test('returns empty string for empty input', () => {
        // Act
        const result = extractFilename('');

        // Assert
        expect(result).toBe('');
    });
});

describe('stripProtocol', () => {
    test('strips https:// prefix', () => {
        // Arrange
        const url = 'https://example.com/f';

        // Act
        const result = stripProtocol(url);

        // Assert
        expect(result).toBe('example.com/f');
    });

    test('strips ftp:// prefix', () => {
        // Arrange
        const url = 'ftp://h/f';

        // Act
        const result = stripProtocol(url);

        // Assert
        expect(result).toBe('h/f');
    });

    test('returns url unchanged when no known protocol prefix present', () => {
        // Arrange
        const url = 'example.com/f';

        // Act
        const result = stripProtocol(url);

        // Assert
        expect(result).toBe('example.com/f');
    });
});

describe('extractProtocol', () => {
    test('returns ftp for ftp:// URLs', () => {
        // Arrange
        const url = 'ftp://files.example.com/archive.tar.gz';

        // Act
        const result = extractProtocol(url);

        // Assert
        expect(result).toBe('ftp');
    });

    test('returns https for https:// URLs', () => {
        // Arrange
        const url = 'https://example.com/file.zip';

        // Act
        const result = extractProtocol(url);

        // Assert
        expect(result).toBe('https');
    });

    test('returns https for bare host (no protocol prefix)', () => {
        // Arrange
        const url = 'example.com/file.zip';

        // Act
        const result = extractProtocol(url);

        // Assert
        expect(result).toBe('https');
    });
});
