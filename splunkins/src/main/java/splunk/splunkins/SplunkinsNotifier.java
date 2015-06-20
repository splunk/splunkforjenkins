package splunk.splunkins;

import hudson.tasks.Notifier;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkinsNotifier extends Notifier {
    public int maxLines;
    public boolean failbuild;

    @DataBoundConstructor
    public SplunkinsNotifier

}
// TOMORROW:
// - Update Jenkins debug jobs with echo statement
// - Change keys for jenkins masters
