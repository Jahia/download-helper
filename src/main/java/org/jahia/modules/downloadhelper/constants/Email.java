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
    public static final String DOWNLOAD_ASKED_SUBJECT = "Download asked";
    public static final String DOWNLOAD_COMPLETED_SUBJECT = "Download completed";
    public static final String DOWNLOAD_FAILED_SUBJECT = "Download failed";

    private Email() {
    }
}
