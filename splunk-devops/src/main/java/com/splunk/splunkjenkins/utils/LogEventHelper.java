package com.splunk.splunkjenkins.utils;

import com.google.common.collect.ImmutableMap;
import com.splunk.splunkjenkins.model.CoverageMetricsAdapter;
import groovy.lang.GroovyClassLoader;
import hudson.scm.SCM;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import shaded.splk.com.google.gson.FieldNamingStrategy;
import shaded.splk.com.google.gson.Gson;
import shaded.splk.com.google.gson.GsonBuilder;
import com.splunk.splunkjenkins.Constants;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.console.ConsoleNote;
import hudson.model.*;
import hudson.model.Queue;
import hudson.model.queue.WorkUnit;
import hudson.node_monitors.NodeMonitor;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.ByteArrayOutputStream2;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import shaded.splk.org.apache.http.HttpResponse;
import shaded.splk.org.apache.http.client.HttpClient;
import shaded.splk.org.apache.http.client.entity.GzipCompressingEntity;
import shaded.splk.org.apache.http.client.methods.HttpPost;
import shaded.splk.org.apache.http.client.utils.URIBuilder;
import shaded.splk.org.apache.http.entity.StringEntity;
import shaded.splk.org.apache.http.util.EntityUtils;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.splunk.splunkjenkins.Constants.*;
import static com.splunk.splunkjenkins.model.EventType.BATCH_JSON;
import static com.splunk.splunkjenkins.model.EventType.JENKINS_CONFIG;
import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang.reflect.MethodUtils.getAccessibleMethod;

public class LogEventHelper {
    //see also hudson.util.wrapToErrorSpan
    private static final Pattern ERROR_SPAN_CONTENT = Pattern.compile("error.*?>(.*?)</span>", Pattern.CASE_INSENSITIVE);
    public static final String SEPARATOR = "    ";
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(LogEventHelper.class.getName());
    private static final String JSON_CHANNEL_ID = UUID.randomUUID().toString().toUpperCase();
    private static final String RAW_CHANNEL_ID = UUID.randomUUID().toString().toUpperCase();
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().setFieldNamingStrategy(new LowerCaseStrategy())
            .setDateFormat(LOG_TIME_FORMAT)
            .registerTypeAdapter(CoverageMetricsAdapter.CoverageDetail.class, new CoverageDetailJsonSerializer())
            .registerTypeAdapter(Double.class, new SpecialDoubleAdapter())
            .registerTypeAdapter(Float.class, new SpecialFloatAdapter())
            .create();
    private static final Map<String, Long> HUMAN_READABLE_SIZE = ImmutableMap.<String, Long>builder()
            .put("KB", 1024L)
            .put("kB", 1000L)
            .put("KiB", 1024L)
            .put("MB", 1024 * 1024L)
            .put("MiB", 1024 * 1024L)
            .put("GB", 1024 * 1024 * 1024L)
            .build();

    private static boolean gzipEnabled = !Boolean.getBoolean(LogEventHelper.class.getName() + ".disableGzip");

    public static HttpPost buildPost(EventRecord record, SplunkJenkinsInstallation config) {
        HttpPost postMethod;
        if (config.canPostRaw(record.getEventType())) {
            postMethod = new HttpPost(record.getRawEndpoint(config));
            LOG.log(Level.FINEST, "sending raw data, source=" + record.getSource());
            updateContent(postMethod, record.getMessageString(), false);
            postMethod.setHeader("x-splunk-request-channel", RAW_CHANNEL_ID);
        } else {
            postMethod = new HttpPost(config.getJsonUrl());
            String jsonRecord;
            if (record.getEventType().needSplit()) {
                //http event collector does not support raw event, need split records and append metadata to message body
                StringWriter stout = new StringWriter();
                String[] values = record.getMessageString().split("[\\r\\n]+");
                for (String line : values) {
                    if (!isNullOrEmpty(line)) {
                        EventRecord lineRecord = new EventRecord(line, record.getEventType());
                        lineRecord.setSource(record.getSource());
                        lineRecord.setTime(record.getTime());
                        stout.write(gson.toJson(lineRecord.toMap(config)));
                        stout.write("\n");
                    }
                }
                jsonRecord = stout.toString();
            } else if (record.getEventType() == BATCH_JSON) {
                jsonRecord = record.getMessageString();
            } else {
                jsonRecord = gson.toJson(record.toMap(config));
            }
            LOG.log(Level.FINEST, jsonRecord);
            updateContent(postMethod, jsonRecord, true);
            postMethod.setHeader("x-splunk-request-channel", JSON_CHANNEL_ID);
        }
        postMethod.setHeader("Authorization", "Splunk " + config.getToken());
        return postMethod;
    }

    private static void updateContent(HttpPost postMethod, String message, boolean isJson) {
        StringEntity entity = new StringEntity(message, UTF_8);
        if (isJson) {
            entity.setContentType("application/json; profile=urn:splunk:event:1.0; charset=utf-8");
        }
        if (gzipEnabled && entity.getContentLength() > GZIP_THRESHOLD) {
            postMethod.setEntity(new GzipCompressingEntity(entity));
        } else {
            postMethod.setEntity(entity);
        }
    }

    public static FormValidation verifyHttpInput(SplunkJenkinsInstallation config) {
        HttpPost post = buildPost(new EventRecord("ping from jenkins plugin", EventType.LOG), config);
        HttpClient client = SplunkLogService.getInstance().getClient();
        try {
            HttpResponse response = client.execute(post);
            if (response.getStatusLine().getStatusCode() != 200) {
                String reason = response.getStatusLine().getReasonPhrase();
                if (response.getStatusLine().getStatusCode() == 400) {
                    return FormValidation.error("Incorrect index name or do not have write permission to the default index, please check MetaData configuration");
                } else {
                    return FormValidation.error("Token:" + config.getToken() + " response:" + reason);
                }
            }
            EntityUtils.consume(response.getEntity());
            post.releaseConnection();
            //check if raw events is supported
            config.setRawEventEnabled(true);
            post = buildPost(new EventRecord("ping from jenkins plugin\nraw event ping", EventType.LOG), config);
            response = client.execute(post);
            SplunkJenkinsInstallation globalConfig = SplunkJenkinsInstallation.get();
            if (response.getStatusLine().getStatusCode() != 200 && globalConfig != null) {
                //it is ok to use json but update global flag to turn off raw handling
                SplunkJenkinsInstallation.get().setRawEventEnabled(false);
                return FormValidation.ok("Splunk connection verified but raw event is not supported");
            }
        } catch (IOException e) {
            return FormValidation.error(e.getMessage());
        } finally {
            post.releaseConnection();
        }
        return FormValidation.ok("Splunk connection verified");
    }

    /**
     * the logical extracted from PlainTextConsoleOutputStream
     * console annotation will be removed, e.g.
     * Input:Started by user ESC[8mha:AAAAlh+LCAAAAAAAAP9b85aBtbiIQTGjNKU4P08vOT+vOD8nVc83PyU1x6OyILUoJzMv2y+/JJUBAhiZGBgqihhk0NSjKDWzXb3RdlLBUSYGJk8GtpzUvPSSDB8G5tKinBIGIZ+sxLJE/ZzEvHT94JKizLx0a6BxUmjGOUNodHsLgAzOEgYu/dLi1CL9vNKcHACFIKlWvwAAAA==ESC[0manonymous
     * Output:Started by user anonymous
     *
     * @param in     the byte array
     * @param length how many bytes we want to read in
     * @param out    write max(length) to out
     * @see hudson.console.PlainTextConsoleOutputStream
     */
    public static void decodeConsoleBase64Text(byte[] in, int length, ByteArrayOutputStream2 out) {
        int next = ConsoleNote.findPreamble(in, 0, length);

        // perform byte[]->char[] while figuring out the char positions of the BLOBs
        int written = 0;
        while (next >= 0) {
            if (next > written) {
                out.write(in, written, next - written);
                written = next;
            } else {
                assert next == written;
            }

            int rest = length - next;
            ByteArrayInputStream b = new ByteArrayInputStream(in, next, rest);

            try {
                ConsoleNote.skip(new DataInputStream(b));
            } catch (IOException ex) {
                Logger.getLogger(LogEventHelper.class.getName()).log(Level.SEVERE, "failed to filter blob", ex);
            }

            int bytesUsed = rest - b.available(); // bytes consumed by annotations
            written += bytesUsed;


            next = ConsoleNote.findPreamble(in, written, length - written);
        }
        // finish the remaining bytes->chars conversion
        out.write(in, written, length - written);
    }

    public static boolean nonEmpty(String value) {
        return emptyToNull(value) != null;
    }

    /**
     * This method may trigger load user operations.
     *
     * @return User display name
     */
    public static String getUserName() {
        User user = User.current();
        if (user == null) {
            return "anonymous";
        } else {
            return user.getDisplayName();
        }
    }

    private static long getUsedHeapSize() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long usedHeap = usage.getUsed();
        return usedHeap;
    }

    /**
     * @return Jenkins master statistics with timestamp
     */
    public static Map<String, Object> getMasterStats() {
        Jenkins instance = Jenkins.getInstance();
        int computerSize = instance.getComputers().length;
        int totalExecutors = instance.overallLoad.computeTotalExecutors();
        int queueLength = instance.overallLoad.computeQueueLength();
        int idleExecutors = instance.overallLoad.computeIdleExecutors();
        Map<String, Object> event = new HashMap<>();
        event.put("queue_length", queueLength);
        event.put("total_computers", computerSize);
        event.put("idle_executors", idleExecutors);
        event.put("total_executors", totalExecutors);

        long heapSize = getUsedHeapSize();
        long heapMB = heapSize >> 20;
        event.put("heap_size_mb", heapMB);
        ThreadMXBean threadMXbean = ManagementFactory.getThreadMXBean();
        int threadCount = threadMXbean.getThreadCount();
        int daemonThreadCount = threadMXbean.getDaemonThreadCount();
        event.put("thread_count", threadCount);
        event.put("daemon_count", daemonThreadCount);
        //event.put("time", new Date());
        return event;
    }

    public static int sendFiles(Run build, FilePath ws, Map<String, String> envVars, TaskListener listener,
                                String includes, String excludes, boolean sendFromSlave, long maxFileSize) {
        int eventCount = 0;
        if (ws == null) {
            return eventCount;
        }
        final String expanded = Util.replaceMacro(includes, envVars);
        final String exclude = Util.replaceMacro(excludes, envVars);
        try {
            if (!ws.exists()) {
                LOG.warning("ws doesn't exist: " + ws.getRemote());
                return eventCount;
            }

            final FilePath[] paths = ws.list(expanded, exclude);
            if (paths.length == 0) {
                LOG.warning("can not find files using includes:" + includes + " excludes:" + excludes + " in workspace:" + ws.getRemote());
                return eventCount;
            }
            Map configMap = SplunkJenkinsInstallation.get().toMap();
            LogFileCallable fileCallable = new LogFileCallable(ws.getRemote(), build.getUrl(), configMap, sendFromSlave, maxFileSize);
            eventCount = fileCallable.sendFiles(paths);
            listener.getLogger().println("sent " + Arrays.toString(paths) + " to splunk in " + eventCount + " events");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "failed to archive files", e);
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "interrupted while archiving file", e);
        }
        return eventCount;
    }

    public static long parseFileSize(String size) {
        if (emptyToNull(size) == null) {
            return 0;
        }
        try {
            for (Map.Entry<String, Long> keyPair : HUMAN_READABLE_SIZE.entrySet()) {
                String key = keyPair.getKey();
                Long value = keyPair.getValue();
                if (size.endsWith(key)) {
                    String numberPart = size.substring(0, size.length() - key.length());
                    if (numberPart.contains(".")) {
                        return (long) (value * Float.parseFloat(numberPart));
                    }
                    return value * Long.parseLong(numberPart);
                }
            }
            return Long.parseLong(size);
        } catch (NumberFormatException ex) {
            LOG.log(Level.SEVERE, "invalid number " + size);
            return 0;
        }
    }

    /**
     * @param run Jenkins Run
     * @return the user who triggered the build or upstream build
     */
    public static String getTriggerUserName(Run run) {
        String userName = "anonymous";
        //maven modules triggered by ModuleSet project, no causes available
        if (run.getParent().getClass().getName().equals("hudson.maven.MavenModule")) {
            return "(maven)";
        }
        Cause.UpstreamCause upstreamCause = null;
        String triggerUserName = null;
        findUserLoop:
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                triggerUserName = getUsernameOrTimer(cause);
                //check if we located the user name
                if (triggerUserName != null) {
                    break findUserLoop;
                } else if (upstreamCause == null && cause instanceof Cause.UpstreamCause) {
                    upstreamCause = (Cause.UpstreamCause) cause;
                }
            }
        }
        if (triggerUserName != null) {
            userName = triggerUserName;
        } else if (upstreamCause != null) {
            userName = getUpStreamUser(upstreamCause);
        }
        return userName;
    }

    /**
     * get the user name from UpstreamCause, also recursive check top level upstreams
     * e.g.<pre>
     * Started by upstream project "jobs_list" build number 47
     *  originally caused by:
     *  Started by upstream project "trigger_job" build number 2
     *      originally caused by:
     *      Started by user Jonh doe
     * </pre>
     *
     * @param upstreamCause
     * @return
     */
    private static String getUpStreamUser(Cause.UpstreamCause upstreamCause) {
        for (Cause upCause : upstreamCause.getUpstreamCauses()) {
            if (upCause instanceof Cause.UpstreamCause) {
                return getUpStreamUser((Cause.UpstreamCause) upCause);
            } else {
                String userName = getUsernameOrTimer(upCause);
                if (userName != null) {
                    return userName;
                }
            }
        }
        return null;
    }

    private static String getUsernameOrTimer(Cause cause) {
        if (cause instanceof Cause.UserIdCause) {
            return ((Cause.UserIdCause) cause).getUserName();
        } else if (cause instanceof TimerTrigger.TimerTriggerCause) {
            return "(timer)";
        } else if (cause instanceof SCMTrigger.SCMTriggerCause) {
            return "(scm)";
        }
        return null;
    }

    public static class UrlQueryBuilder {
        private Map<String, String> query = new HashMap();

        public static String toString(Map<String, String> queryParameters) {
            URIBuilder builder = new URIBuilder();
            for (Map.Entry<String, String> keyPair : queryParameters.entrySet()) {
                String key = keyPair.getKey();
                String value = keyPair.getValue();
                builder.addParameter(key, value);
            }
            try {
                URI uri = builder.build();
                String queryUrl = uri.toString();
                //remove first ?
                if (StringUtils.indexOf(queryUrl, "?") == 0) {
                    return queryUrl.substring(1, queryUrl.length());
                } else {
                    return queryUrl;
                }
            } catch (URISyntaxException e) {
                LOG.log(Level.SEVERE, "failed to encode url", e);
                return "";
            }
        }

        public UrlQueryBuilder putIfAbsent(String key, String value) {
            if (nonEmpty(value) && !"null".equals(value)) {
                //Map.putIfAbsent was @since 1.8, use get and check null to check
                Object existValue = query.get(key);
                if (existValue == null) {
                    query.put(key, value);
                }
            }
            return this;
        }

        public Map getQueryMap() {
            return Collections.unmodifiableMap(query);
        }

        public String build() {
            return UrlQueryBuilder.toString(this.query);
        }
    }

    static class LowerCaseStrategy implements FieldNamingStrategy {
        @Override
        public String translateName(final Field f) {
            return f.getName().toLowerCase();
        }
    }

    /**
     * @param computer
     * @return the computer name
     */
    private static String getNodeName(Computer computer) {
        if (computer == null) {
            return "N/A";
        }
        if (computer instanceof Jenkins.MasterComputer) {
            return Constants.MASTER;
        } else {
            return computer.getName();
        }
    }

    public static Map<String, Object> getComputerStatus(Computer computer) {
        String nodeName;
        Map slaveInfo = new HashMap();
        if (computer == null) {
            return slaveInfo;
        }
        nodeName = getNodeName(computer);
        slaveInfo.put(NODE_NAME, nodeName);
        slaveInfo.put(Constants.TAG, Constants.SLAVE_TAG_NAME);
        Node slaveNode = computer.getNode();
        if (slaveNode != null) {
            slaveInfo.put("label", slaveNode.getLabelString());
        }
        slaveInfo.put("status", "updated");
        slaveInfo.put("num_executors", computer.getNumExecutors());
        slaveInfo.put("is_idle", computer.isIdle());
        slaveInfo.put("is_online", computer.isOnline());
        if (computer.isOffline()) {
            String offlineReason = computer.getOfflineCauseReason();
            //hudson.model.Messages.Hudson_NodeBeingRemoved() can not used externally in latest version
            if (StringUtils.contains(offlineReason, "removed")) {
                //overwrite num_executors to zero
                slaveInfo.put("num_executors", 0);
                slaveInfo.put("removed", "true");
            }
            slaveInfo.put("offline_reason", offlineReason);
            slaveInfo.put("connecting", computer.isConnecting());
        }
        slaveInfo.put("url", Jenkins.getInstance().getRootUrl() + computer.getUrl());
        long connectTime = computer.getConnectTime();
        if (connectTime != 0) {
            slaveInfo.put("connect_time", Util.XS_DATETIME_FORMATTER.format(connectTime));
        } else {
            //slave is offline or disconnected
            slaveInfo.put("connect_time", 0);
        }
        slaveInfo.put("uptime", getUpTime(computer));
        return slaveInfo;
    }

    public static List<Map> getRunningJob() {
        List<Map> builds = new ArrayList<>();
        for (Computer computer : Jenkins.getInstance().getComputers()) {
            List<Run> runList = new ArrayList<>();
            for (Executor executor : computer.getExecutors()) {
                Run run = getRunningJob(executor);
                if (run != null) {
                    runList.add(run);
                }
            }
            for (Executor executor : computer.getOneOffExecutors()) {
                Run run = getRunningJob(executor);
                if (run != null) {
                    runList.add(run);
                }
            }
            for (Run run : runList) {
                Map buildInfo = new HashMap();
                buildInfo.put(Constants.BUILD_ID, run.getUrl());
                buildInfo.put(Constants.TAG, Constants.JOB_EVENT_MONITOR);
                buildInfo.put(Constants.NODE_NAME, getNodeName(computer));
                buildInfo.put("job_name", run.getParent().getFullName());
                buildInfo.put("build_number", run.getNumber());
                buildInfo.put("job_duration", getRunDuration(run));
                builds.add(buildInfo);
            }
        }
        return builds;
    }

    private static Run getRunningJob(Executor executor) {
        Run run = null;
        Queue.Executable executable = executor.getCurrentExecutable();
        WorkUnit workUnit = executor.getCurrentWorkUnit();
        if (executable == null && workUnit != null) {
            executable = workUnit.getExecutable();
        }
        if (executable != null && executable instanceof Run) {
            run = (Run) executable;
        }
        return run;
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private static Object getUpTime(Computer computer) {
        Method method = getAccessibleMethod(computer.getClass(), "getUptime", new Class<?>[0]);
        if (method != null) {
            try { //cloud slave defined getUptime method
                return method.invoke(computer, new Object[0]);
            } catch (Exception e) {
                //just ignore
            }
        }
        return null;
    }

    private static Map<String, Object> getMonitorData(Computer computer, NodeMonitor monitor) {
        Map monitorStatus = new HashMap();
        Object data = monitor.data(computer);
        if (data != null) {
            String monitorName = monitor.getClass().getSimpleName();
            //Jenkins monitors are designed for web pages, toString() OR toHtml may contain html code
            String monitorData;
            Method method = getAccessibleMethod(data.getClass(), "toHtml", new Class<?>[0]);
            if (method != null) {
                try {
                    monitorData = (String) method.invoke(data, new Object[0]);
                } catch (Exception e) {
                    monitorData = data.toString();
                }
            } else {
                monitorData = data.toString();
            }
            Matcher matcher = ERROR_SPAN_CONTENT.matcher(monitorData);
            if (matcher.find()) {
                monitorStatus.put(monitorName, "warning:" + matcher.group(1));
            } else {
                monitorStatus.put(monitorName, monitorData);
            }
        }
        return monitorStatus;
    }

    /**
     * @return a map with slave name as key and monitor result as value
     * monitor result is a map with monitor name as key, monitor data as value
     */
    public static Map<String, Map<String, Object>> getSlaveStats() {
        Map<String, Map<String, Object>> slaveStatusMap = new HashMap<>();
        Computer[] computers = Jenkins.getInstance().getComputers();
        if (computers == null || computers.length == 0) {
            return slaveStatusMap;
        }
        Collection<NodeMonitor> monitors = ComputerSet.getMonitors();
        for (Computer computer : computers) {
            Map slaveInfo = new HashMap();
            slaveInfo.put(EVENT_CAUSED_BY, "monitor");
            slaveInfo.putAll(getComputerStatus(computer));
            for (NodeMonitor monitor : monitors) {
                slaveInfo.putAll(getMonitorData(computer, monitor));
            }
            slaveStatusMap.put((String) slaveInfo.get(NODE_NAME), slaveInfo);
        }
        return slaveStatusMap;
    }

    /**
     * @param run the build
     * @return build env with masked password variables
     */
    public static EnvVars getEnvironment(Run run) {
        EnvVars vars;
        Map<String, String> maskPasswords = new HashMap<>();
        List<ParametersAction> parameterActions = run.getActions(ParametersAction.class);
        for (ParametersAction parameters : parameterActions) {
            for (ParameterValue p : parameters) {
                if ((p instanceof PasswordParameterValue)) {
                    maskPasswords.put(p.getName(), MASK_PASSWORD);
                }
            }
        }
        try {
            vars = run.getEnvironment(BuildListener.NULL);
            //overwrite with masked password
            vars.putAll(maskPasswords);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "failed to get build environment for build {}", run.getUrl());
            vars = new EnvVars();
        }
        return vars;
    }

    /**
     * @param run       the build
     * @param completed the task is completed to compute scm info
     * @return build variables with password masked
     */
    public static Map<String, Object> getBuildVariables(Run run, boolean completed) {
        Map<String, Object> values = new HashMap<>();
        List<ParametersAction> parameterActions = run.getActions(ParametersAction.class);
        for (ParametersAction parameters : parameterActions) {
            for (ParameterValue p : parameters) {
                if (p == null) continue;
                if (!p.isSensitive()) {
                    values.put(p.getName(), p.getValue());
                } else {
                    values.put(p.getName(), MASK_PASSWORD);
                }
            }
        }
        if (completed) {
            appendScm(values, run);
        }
        return values;
    }

    /**
     * @param run the build
     * @return build variables with password masked
     */
    public static Map<String, Object> getBuildVariables(Run run) {
        return getBuildVariables(run, true);
    }

    public static void logUserAction(String user, String message) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(JENKINS_CONFIG)) {
            return;
        }
        Map logInfo = new HashMap<>();
        logInfo.put(TAG, "audit_trail");
        logInfo.put("message", message);
        logInfo.put("user", user);
        SplunkLogService.getInstance().send(logInfo, JENKINS_CONFIG, AUDIT_SOURCE);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public static void updateSlaveInfoAsync(final String nodeName) {
        Computer.threadPoolForRemoting.submit(new Runnable() {
            @Override
            public void run() {
                if (nodeName != null) {
                    Node node = Jenkins.getInstance().getNode(nodeName);
                    if (node != null && node.toComputer() != null) {
                        Computer computer = node.toComputer();
                        Map event = getComputerStatus(computer);
                        if (!event.isEmpty()) {
                            SplunkLogService.getInstance().send(event, SLAVE_INFO);
                        }
                    }
                }
            }
        });
    }

    /**
     * @param configPath the absolute path
     * @return the relative path to <tt>JENKINS_HOME</tt> directory
     */
    public static String getRelativeJenkinsHomePath(String configPath) {
        String jenkinsHome = Jenkins.getInstance().getRootDir().getPath();
        String relativePath = configPath;
        if (configPath.startsWith(jenkinsHome)) {
            relativePath = configPath.substring(jenkinsHome.length() + 1);
        }
        return relativePath;
    }

    public static String getDefaultDslScript() {
        String exampleText = "//post script section";
        try (InputStream input = LogEventHelper.class.getClassLoader().getResourceAsStream("sample.groovy")) {
            exampleText = IOUtils.toString(input);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "failed to read example.groovy", e);
        }
        return exampleText;
    }

    /**
     * @param script user input script to validate
     * @return error message if there is any
     */
    public static FormValidation validateGroovyScript(String script) {
        FormValidation validationResult = GroovySandbox.checkScriptForCompilationErrors(script,
                new GroovyClassLoader(Jenkins.getInstance().getPluginManager().uberClassLoader));
        if (validationResult.kind == FormValidation.Kind.OK) {
            return ScriptApproval.get().checking(script, GroovyLanguage.get());
        } else {
            return validationResult;
        }
    }

    /**
     * check if the project has publisher
     *
     * @param shortClassName common used publishers are
     * @param build          jenkins build
     * @return true if the publisher is defined, false otherwise
     */

    public static boolean hasPublisherName(String shortClassName, Run build) {
        boolean found = false;
        if (!(build instanceof AbstractBuild)) {
            return found;
        }
        Descriptor<Publisher> publisherDescriptor = Jenkins.getInstance().getPublisher(shortClassName);
        if (publisherDescriptor == null) {
            return found;
        }
        Class clazz = publisherDescriptor.clazz;
        DescribableList<Publisher, Descriptor<Publisher>> publishers = ((AbstractBuild) build).getProject().getPublishersList();
        for (Publisher publisher : publishers) {
            if (clazz.isInstance(publisher)) {
                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * @param run an execution of job
     * @return job duration
     */
    public static float getRunDuration(Run run) {
        float duration = run.getDuration() / 1000f;
        if (duration < 0.01f || run.isBuilding()) {
            //workflow job duration is updated after job completed
            //not available in onCompleted listener
            duration = Math.max(0, (System.currentTimeMillis() - run.getStartTimeInMillis()) / 1000f);
        }
        return duration;
    }

    public static void appendScm(Map eventToAppend, Run run) {
        Map<String, Object> scmInfo = getScmInfo(run);
        //append scm info build parameter if no conflicts
        for (Map.Entry<String, Object> scmEntry : scmInfo.entrySet()) {
            if (!eventToAppend.containsKey(scmEntry.getKey())) {
                eventToAppend.put(scmEntry.getKey(), scmEntry.getValue());
            }
        }
    }

    public static Map<String, Object> getScmInfo(Run build) {
        SCMTriggerItem scmTrigger = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(build.getParent());
        if (scmTrigger == null) {
            return Collections.emptyMap();
        }
        Collection<? extends SCM> scmConfigs = scmTrigger.getSCMs();
        Map<String, Object> event = new HashMap<>();
        Map<String, Object> singleEvent = new HashMap<>();
        EnvVars envVars = new EnvVars();
        for (SCM scm : scmConfigs) {
            if (build instanceof AbstractBuild) {
                scm.buildEnvVars((AbstractBuild) build, envVars);
            }
            String scmName = scm.getClass().getName();
            if (!event.containsKey(scmName)) {
                singleEvent = getScmInfo(scmName, envVars);
                event.put(scmName, singleEvent);
            }
        }
        if (event.size() == 1) {
            return singleEvent;
        } else { //there are multiple scm
            return event;
        }
    }

    /**
     * @param scmName scm class name
     * @param envVars environment variables
     * @return scm information, we only support git,svn and p4
     */
    public static Map<String, Object> getScmInfo(String scmName, EnvVars envVars) {
        Map<String, Object> event = new HashMap<>();
        //not support GIT_URL_N or SVN_URL_n
        // scm can be found at https://wiki.jenkins-ci.org/display/JENKINS/Plugins
        switch (scmName) {
            case "hudson.plugins.git.GitSCM":
                event.put("scm", "git");
                event.put("scm_url", getScmURL(envVars, "GIT_URL"));
                event.put("branch", envVars.get("GIT_BRANCH"));
                event.put("revision", envVars.get("GIT_COMMIT"));
                break;
            case "hudson.scm.SubversionSCM":
                event.put("scm", "svn");
                event.put("scm_url", getScmURL(envVars, "SVN_URL"));
                event.put("revision", envVars.get("SVN_REVISION"));
                break;
            case "org.jenkinsci.plugins.p4.PerforceScm":
                event.put("scm", "p4");
                event.put("p4_client", envVars.get("P4_CLIENT"));
                event.put("revision", envVars.get("P4_CHANGELIST"));
                break;
            case "hudson.plugins.mercurial.MercurialSCM":
                event.put("scm", "hg");
                event.put("scm_url", envVars.get("MERCURIAL_REPOSITORY_URL"));
                event.put("branch", envVars.get("MERCURIAL_REVISION_BRANCH"));
                event.put("revision", envVars.get("MERCURIAL_REVISION"));
                break;
            case "hudson.scm.NullSCM":
                break;
            default:
                event.put("scm", scmName);
        }
        return event;
    }

    /**
     * @param envVars environment variables
     * @param prefix  scm prefix, such as GIT_URL, SVN_URL
     * @return parsed scm urls from build env, e.g. GIT_URL_1, GIT_URL_2, ... GIT_URL_10 or GIT_URL
     */
    public static String getScmURL(EnvVars envVars, String prefix) {
        String value = envVars.get(prefix);
        if (value == null) {
            List<String> urls = new ArrayList<>();
            //just probe max 10 url
            for (int i = 0; i < 10; i++) {
                String probe_url = envVars.get(prefix + "_" + i);
                if (probe_url != null) {
                    urls.add(Util.replaceMacro(probe_url, envVars));
                } else {
                    break;
                }
            }
            if (!urls.isEmpty()) {
                value = StringUtils.join(urls, ",");
            }
        } else {
            value = Util.replaceMacro(value, envVars);
        }
        return value;
    }

    public static String toJson(EventRecord record) {
        if (record == null) {
            return "\"empty record\"";
        }
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        return gson.toJson(record.toMap(config));
    }

    public static String getBuildVersion() {
        Properties properties = new Properties();
        try (InputStream pomInput = LogEventHelper.class.getResourceAsStream("/META-INF/maven/com.splunk.splunkins/splunk-devops/pom.properties")) {
            if (pomInput != null) {
                properties.load(pomInput);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "failed to open file splunk-devops/pom.properties", e);
        }
        return properties.getProperty("version", "snapshot");
    }

    /**
     * @param run Jenkins job run
     * @return causes separated by comma
     */
    public static String getBuildCauses(Run run) {
        Set<String> causes = new LinkedHashSet<>();
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                causes.add(cause.getShortDescription());
            }
        }
        for (InterruptedBuildAction action : run.getActions(InterruptedBuildAction.class)) {
            for (CauseOfInterruption cause : action.getCauses()) {
                causes.add(cause.getShortDescription());
            }
        }
        return StringUtils.join(causes, ", ");
    }
}
