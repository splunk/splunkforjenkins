package com.splunk.splunkjenkins.model;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @since 1.4
 * Helper class for metdata configure
 */
public class MetaDataConfigItem implements Describable<MetaDataConfigItem> {
    private static final String DISABLED_KEY = "disabled";
    private static final Map<String, String> CONFIG_ITEM_MAP = new ImmutableMap.Builder<String, String>().put("Index", "index")
            .put("Source Type", "sourcetype").put("Disabled", DISABLED_KEY).build();
    @Nonnull
    private String dataSource;
    @Nonnull
    private String keyName;
    //can only be null if enabled is false
    private String value;

    @Nonnull
    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(@Nonnull String dataSource) {
        this.dataSource = dataSource;
    }

    @Nonnull
    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(@Nonnull String keyName) {
        this.keyName = keyName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @DataBoundConstructor
    public MetaDataConfigItem(String dataSource, String keyName, String value) {
        this.dataSource = dataSource;
        this.keyName = keyName;
        this.value = value;
    }

    @Override
    public String toString() {
        String prefix = dataSource.toLowerCase() + ".";
        if ("default".equals(dataSource)) {
            prefix = "";
        }
        if (DISABLED_KEY.equals(this.keyName)) {
            return prefix + "enabled=false";
        } else {
            return prefix + keyName + "=" + value;
        }
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MetaDataConfigItem) || obj == null) {
            return false;
        }
        return this.toString().equals(obj.toString());
    }

    @Override
    public Descriptor<MetaDataConfigItem> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MetaDataConfigItem> {

        @Override
        public String getDisplayName() {
            return "Metadata config";
        }

        /**
         * This method determines the values of the album drop-down list box.
         *
         * @return options for selection
         */
        public ListBoxModel doFillDataSourceItems() {
            ListBoxModel m = new ListBoxModel();
            m.add("Build Event", EventType.BUILD_EVENT.toString());
            m.add("Build Report", EventType.BUILD_REPORT.toString());
            m.add("Console Log", EventType.CONSOLE_LOG.toString());
            m.add("Jenkins Config", EventType.JENKINS_CONFIG.toString());
            m.add("Log File", EventType.FILE.toString());
            m.add("Queue Information", EventType.QUEUE_INFO.toString());
            m.add("Slave Information", EventType.SLAVE_INFO.toString());
            m.add("Default", "default");
            return m;
        }

        /**
         * This method determines the values of the album drop-down list box.
         *
         * @return options for selection
         */
        public static ListBoxModel doFillKeyNameItems() {
            ListBoxModel m = new ListBoxModel();
            for (Map.Entry<String, String> entry : CONFIG_ITEM_MAP.entrySet()) {
                m.add(entry.getKey(), entry.getValue());
            }
            return m;
        }
    }

    public static Set<MetaDataConfigItem> loadProps(String properties) {
        Set<MetaDataConfigItem> config = new HashSet<>();
        if (properties != null) {
            Properties metaDataConfigProps = new Properties();
            try {
                metaDataConfigProps.load(new StringReader(properties));
                for (EventType eventType : EventType.values()) {
                    //backward compatible, xx.enabled=false
                    if ("false".equals(metaDataConfigProps.getProperty(eventType.getKey("enabled")))) {
                        config.add(new MetaDataConfigItem(eventType.toString(), DISABLED_KEY, ""));
                    } else {
                        for (String suffix : CONFIG_ITEM_MAP.values()) {
                            String lookupKey = eventType.getKey(suffix);
                            if (metaDataConfigProps.containsKey(lookupKey)) {
                                config.add(new MetaDataConfigItem(eventType.toString(), suffix,
                                        metaDataConfigProps.getProperty(lookupKey)));
                            }
                        }
                    }
                }
                //add default
                for (String keyName : CONFIG_ITEM_MAP.values()) {
                    if (metaDataConfigProps.containsKey(keyName)) {
                        config.add(new MetaDataConfigItem("default", keyName,
                                metaDataConfigProps.getProperty(keyName)));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return config;
    }

    /**
     * Convert config set to plain text string, just to keep backward compatibility
     *
     * @param configs config items
     * @return java property file content
     */
    public static String toString(Set<MetaDataConfigItem> configs) {
        StringBuffer sbf = new StringBuffer();
        if (configs == null || configs.isEmpty()) {
            return "";
        }
        for (MetaDataConfigItem config : configs) {
            sbf.append(config.toString()).append("\n");
        }
        return sbf.toString();
    }

    public String getCssDisplay() {
        if (DISABLED_KEY.equals(this.keyName)) {
            return "display:none";
        } else {
            return "display:''";
        }
    }
}
