package org.jahia.modules.downloadhelper.constants;

public final class Email {

    public static final String DOWNLOAD_BODY = "Hi,\n"
            + "\n"
            + "We're sending this email following a \"%s\".\n"
            + "\n"
            + "    IP     : %s\n"
            + "    Time   : %s\n"
            + "    User   : %s\n"
            + "    File   : %s\n"
            + "    URL    : %s\n"
            + "\n"
            + "\n"
            + "This email is meant to raise awareness about the state of your system \n"
            + "and to help you manage it.\n"
            + "\n"
            + "Regards,";
    public static final String DOWNLOAD_FOLDER_CREATION_FAILED_BODY = "Hi,\n"
            + "\n"
            + "The download could not be started because the download folder could not be created.\n"
            + "\n"
            + "    Folder : %s\n"
            + "    IP     : %s\n"
            + "    Time   : %s\n"
            + "    User   : %s\n"
            + "    File   : %s\n"
            + "    URL    : %s\n"
            + "\n"
            + "Please check the server filesystem permissions.\n"
            + "\n"
            + "Regards,";
    public static final String DOWNLOAD_INSUFFICIENT_SPACE_BODY = "Hi,\n"
            + "\n"
            + "The download could not be started because there is not enough disk space.\n"
            + "\n"
            + "    Folder    : %s\n"
            + "    Required  : %s\n"
            + "    Available : %s\n"
            + "    IP        : %s\n"
            + "    Time      : %s\n"
            + "    User      : %s\n"
            + "    File      : %s\n"
            + "    URL       : %s\n"
            + "\n"
            + "Please free up disk space before retrying.\n"
            + "\n"
            + "Regards,";
    public static final String DOWNLOAD_INSUFFICIENT_SPACE_SUBJECT = "Download failed - insufficient disk space";
    public static final String DOWNLOAD_ASKED_SUBJECT = "Download asked";
    public static final String DOWNLOAD_COMPLETED_SUBJECT = "Download completed";
    public static final String DOWNLOAD_FAILED_SUBJECT = "Download failed";
    public static final String DOWNLOAD_FOLDER_CREATION_FAILED_SUBJECT = "Download failed - folder could not be created";

    private Email() {
    }
}
