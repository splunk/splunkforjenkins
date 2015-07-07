package jenkins.plugins.splunkins;

import hudson.Plugin;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class PluginImpl extends Plugin {
    private final static Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());

    public void start() throws Exception {
        LOGGER.info(Messages.DESCRIPTION);
    }
}
