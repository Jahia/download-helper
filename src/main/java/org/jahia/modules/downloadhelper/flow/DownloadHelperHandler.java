package org.jahia.modules.downloadhelper.flow;

import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileSystemUtils;
import org.jahia.modules.downloadhelper.services.DownloadHelperService;
import org.jahia.services.render.RenderContext;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.execution.RequestContext;

public class DownloadHelperHandler implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadHelperHandler.class);
    private static final long serialVersionUID = -6552768415414069547L;
    private static final String[] UNITS = new String[]{"KiB", "MiB", "GiB", "TiB"};
    private static final int KILO_CONSTANT = 1024;
    private static final String PROPERTY_PROTOCOL = "protocol";
    private static final String PROPERTY_URL = "url";
    private static final String PROPERTY_LOGIN = "login";
    private static final String PROPERTY_PASSWORD = "password";
    private static final String PROPERTY_FILENAME = "filename";
    private static final String PROPERTY_EMAIL = "email";
    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";

    public void download(RequestContext ctx) {

        final String protocol = ctx.getRequestParameters().get(PROPERTY_PROTOCOL);
        final String url = ctx.getRequestParameters().get(PROPERTY_URL);
        final String login = ctx.getRequestParameters().get(PROPERTY_LOGIN);
        final String password = ctx.getRequestParameters().get(PROPERTY_PASSWORD);
        final String filename = ctx.getRequestParameters().get(PROPERTY_FILENAME);
        final String email = ctx.getRequestParameters().get(PROPERTY_EMAIL);
        final String ip = getIp(ctx);
        final String currentUser = getUser(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DownloadHelperService service = DownloadHelperService.getInstance();
                try {
                    service.download(protocol, url, login, password, filename, email, ip, currentUser);
                } catch (IOException e) {
                    LOGGER.error("Download failed: ", e);
                }
            }
        }).start();
    }

    private RenderContext getRenderContext(RequestContext ctx) {
        return (RenderContext) ctx.getExternalContext().getRequestMap().get("renderContext");
    }

    public String getIp(RequestContext ctx) {
        final HttpServletRequest request = getRenderContext(ctx).getRequest();
        String remoteAddress = request.getHeader(REMOTE_ADDRESS_HEADER);
        if (remoteAddress == null) {
            remoteAddress = request.getRemoteAddr();
        }
        return remoteAddress;
    }

    public String getUser(RequestContext ctx) {
        return getRenderContext(ctx).getUser().getUserKey();
    }

    public String getAvailableSpace() throws IOException {
        long availableSpace = FileSystemUtils.freeSpaceKb(DownloadHelperService.DOWNLOAD_FOLDER_PATH);
        if (availableSpace <= 0) {
            return "0";
        }

        int digitGroups = (int) (Math.log10(availableSpace) / Math.log10(KILO_CONSTANT));
        return new DecimalFormat("#,##0.#").format(availableSpace / Math.pow(KILO_CONSTANT, digitGroups))
                + " " + UNITS[digitGroups];
    }

    public String getDownloadFolderPath() {
        return DownloadHelperService.DOWNLOAD_FOLDER_PATH;
    }

    public boolean isProcessingServer() {
        return SettingsBean.getInstance().isProcessingServer();
    }
}
