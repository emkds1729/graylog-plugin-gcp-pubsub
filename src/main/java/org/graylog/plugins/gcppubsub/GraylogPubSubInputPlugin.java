package org.graylog.plugins.gcppubsub;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;
import org.graylog2.plugin.configuration.Configuration;

public class GraylogPubSubInputPlugin implements Plugin {

  @Override
  public PluginMetaData metadata() {
    return new GcpPubSubMetaData();
  }

  @Override
  public Collection<PluginModule> modules () {
    return Collections.<PluginModule>singletonList(new GcpPubSubModule());
  }
}
