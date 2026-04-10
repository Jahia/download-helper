import {gql} from '@apollo/client';

export const GET_DOWNLOAD_HELPER_INFO = gql`
    query DownloadHelperInfo {
        downloadHelperInfo {
            isProcessingServer
            availableSpace
            downloadFolderPath
            isMailActivated
        }
    }
`;

export const GET_DOWNLOAD_HELPER_FILES = gql`
    query DownloadHelperFiles {
        downloadHelperFiles {
            name
            size
            lastModified
        }
    }
`;

export const DELETE_DOWNLOADED_FILE = gql`
    mutation DeleteDownloadedFile($filename: String!) {
        downloadHelperDeleteFile(filename: $filename)
    }
`;

export const TRIGGER_DOWNLOAD = gql`
    mutation TriggerDownload(
        $protocol: String!
        $url: String!
        $filename: String!
        $login: String
        $password: String
        $email: String
    ) {
        downloadHelperTrigger(
            protocol: $protocol
            url: $url
            filename: $filename
            login: $login
            password: $password
            email: $email
        )
    }
`;
