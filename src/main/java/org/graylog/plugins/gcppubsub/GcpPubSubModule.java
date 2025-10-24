package org.graylog.plugins.gcppubsub;

import java.util.Collections;
import java.util.Set;
import org.graylog2.plugin.PluginConfigBean;
import org.graylog2.plugin.PluginModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpPubSubModule extends PluginModule {

  private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubModule.class);

  @Override
  public Set<? extends PluginConfigBean> getConfigBeans() {
    return Collections.emptySet();
  }

  @Override
  protected void configure() {
    addTransport("GcpPubSubTransport", GcpPubSubTransport.class);
    addMessageInput(GcpPubSubInput.class, GcpPubSubInput.Factory.class);
  }
}
