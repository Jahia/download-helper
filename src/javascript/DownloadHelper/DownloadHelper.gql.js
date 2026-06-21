import {gql} from '@apollo/client';

export const GET_DOWNLOAD_HELPER_INFO = gql`
    query DownloadHelperInfo {
        downloadHelper {
            info {
                isProcessingServer
                availableSpace
                downloadFolderPath
                isMailActivated
            }
        }
    }
`;

export const GET_DOWNLOAD_HELPER_FILES = gql`
    query DownloadHelperFiles {
        downloadHelper {
            files {
                name
                size
                lastModified
            }
        }
    }
`;

export const DELETE_DOWNLOADED_FILE = gql`
    mutation DeleteDownloadedFile($filename: String!) {
        downloadHelper {
            deleteFile(filename: $filename)
        }
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
        downloadHelper {
            trigger(
                protocol: $protocol
                url: $url
                filename: $filename
                login: $login
                password: $password
                email: $email
            )
        }
    }
`;
