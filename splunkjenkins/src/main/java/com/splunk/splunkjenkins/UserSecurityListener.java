package com.splunk.splunkjenkins;

import hudson.Extension;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;

import javax.annotation.Nonnull;

import static com.splunk.splunkjenkins.utils.LogEventHelper.logUserAction;

@Extension
public class UserSecurityListener extends SecurityListener {
    @Override
    protected void authenticated(@Nonnull UserDetails details) {

    }

    @Override
    protected void failedToAuthenticate(@Nonnull String username) {
        logUserAction(username, "failedToAuthenticate");
    }

    @Override
    protected void loggedIn(@Nonnull String username) {
        logUserAction(username, "loggedIn");

    }

    @Override
    protected void failedToLogIn(@Nonnull String username) {
        logUserAction(username, "failedToLogIn");

    }

    @Override
    protected void loggedOut(@Nonnull String username) {
        logUserAction(username, "loggedOut");
    }
}
