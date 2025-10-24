package org.graylog.plugins.gcppubsub;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

public class GcpPubSubMetaData implements PluginMetaData {

    private static final String PLUGIN_PROPERTIES = "org.graylog.plugins.gcppubsub.graylog-plugin-gcp-pubsub/graylog-plugin.properties";

    @Override
    public String getUniqueId() {
        return "org.graylog.plugins.gcppubsub.GcpPubSubPlugin";
    }

    @Override
    public String getName() {
        return "GraylogPubSubInputPlugin";
    }

    @Override
    public String getAuthor() {
        return "Keith Sprochi <ksprochi@elementalmachines.com>";
    }

    @Override
    public URI getURL() {
        return URI.create("https://github.com/emkds1729/graylog-plugin-gcp-pubsub");
    }

    @Override
    public Version getVersion() {
        return Version.fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "version", Version.from(0, 0, 0, "unknown"));
    }

    @Override
    public String getDescription() {
        return "Graylog input plugin for Google Cloud Pub/Sub - receive logs from GCP Cloud Logging via Pub/Sub subscriptions";
    }

    @Override
    public Version getRequiredVersion() {
        return Version.fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "graylog.version", Version.from(0, 0, 0, "unknown"));
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return Collections.emptySet();
    }
}
