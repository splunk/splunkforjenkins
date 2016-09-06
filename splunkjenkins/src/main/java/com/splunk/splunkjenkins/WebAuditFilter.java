package com.splunk.splunkjenkins;

import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;
import static com.splunk.splunkjenkins.utils.LogEventHelper.logUserAction;

public class WebAuditFilter implements Filter {
    //we only cares about delete action
    String deleteAction = "doDelete";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String requestUri = req.getPathInfo();
        if (StringUtils.endsWith(requestUri, deleteAction)) {
            String path = requestUri.substring(0, requestUri.length() - deleteAction.length());
            logUserAction(getUserName(), "delete " + path);

        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}
