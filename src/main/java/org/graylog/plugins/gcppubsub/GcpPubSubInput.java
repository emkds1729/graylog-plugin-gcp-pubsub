package org.graylog.plugins.gcppubsub;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import jakarta.inject.Inject;
import org.graylog2.inputs.codecs.RawCodec;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpPubSubInput extends MessageInput {

  private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubInput.class);
  private static final String NAME = "Google Cloud PubSub";

  @AssistedInject
  public GcpPubSubInput(@Assisted Configuration configuration,
                        GcpPubSubTransport.Factory transportFactory,
                        RawCodec.Factory rawCodecFactory,
                        MetricRegistry metricRegistry,
                        LocalMetricRegistry localRegistry,
                        GcpPubSubInput.Config config,
                        GcpPubSubInput.Descriptor descriptor,
                        ServerStatus serverStatus) {
    super(metricRegistry, configuration, transportFactory.create(configuration), localRegistry, rawCodecFactory.create(configuration), config, descriptor, serverStatus);

  }

  @FactoryClass
  public interface Factory extends MessageInput.Factory<GcpPubSubInput> {

    @Override
    GcpPubSubInput create(Configuration configuration);

    @Override
    Config getConfig();

    @Override
    Descriptor getDescriptor();
  }


  public static class Config extends MessageInput.Config {

    @Inject
    public Config(GcpPubSubTransport.Factory transportFactory, RawCodec.Factory codecFactory) {
      super(transportFactory.getConfig(), codecFactory.getConfig());
    }
  }

  public static class Descriptor extends MessageInput.Descriptor {
    @Inject
    public Descriptor() {
      super(NAME, false, "");
    }
  }

}
