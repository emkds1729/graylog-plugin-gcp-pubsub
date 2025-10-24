package org.graylog.plugins.gcppubsub;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import io.netty.channel.ChannelHandler;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.graylog2.inputs.transports.netty.EventLoopGroupFactory;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.inputs.codecs.CodecAggregator;
import org.graylog2.plugin.inputs.transports.NettyTransport;
import org.graylog2.plugin.inputs.transports.Transport;
import org.graylog2.plugin.inputs.util.ThroughputCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpPubSubTransport extends NettyTransport {

  private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubTransport.class);

  private Configuration configuration;
  private GcpPubSubLogRetriever retriever;

  @Nullable
  private CodecAggregator aggregator;

  @AssistedInject
  public GcpPubSubTransport(@Assisted Configuration configuration,
                                           EventLoopGroupFactory eventLoopGroupFactory,
                                           ThroughputCounter throughputCounter,
                                           LocalMetricRegistry localRegistry) {
    super(configuration, eventLoopGroupFactory, throughputCounter, localRegistry);
    this.configuration = configuration;
  }

  @FactoryClass
  public interface Factory extends Transport.Factory<GcpPubSubTransport> {

    @Override
    GcpPubSubTransport create(Configuration configuration);

    @Override
    Config getConfig();
  }

  @ConfigClass
  public static class Config implements Transport.Config {

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
      return ConfigurationRequest.createWithFields(
        new TextField("gcp_project", "GCP Project", "my-gcp-project", "The GCP project ID", ConfigurationField.Optional.NOT_OPTIONAL),
        new TextField("subscription", "PubSub Subscription", "graylog-pull-subscription", "The subscription to the topic to pull logs from", ConfigurationField.Optional.NOT_OPTIONAL),
        new TextField("service_account_key_file", "Service Account Key File", "", "Path to GCP service account JSON key file (optional - uses Application Default Credentials if not specified)", ConfigurationField.Optional.OPTIONAL)
      );
    }
  }

  @Nullable
  @Override
  public SocketAddress getLocalAddress() {
    // This transport does not use network sockets - it pulls from GCP Pub/Sub
    return null;
  }

  @Override
  protected LinkedHashMap<String, Callable<? extends ChannelHandler>> getChildChannelHandlers(MessageInput input) {
    final LinkedHashMap<String, Callable<? extends ChannelHandler>> handlers = new LinkedHashMap<>();
    final CodecAggregator aggregator = getAggregator();
    return handlers;
  }

  @Override
  public void launch(MessageInput input) throws MisfireException {
    retriever = new GcpPubSubLogRetriever(configuration, input);
    retriever.processBatches();
  }

  @Override
  public void stop() {
    retriever.stop();
  }

}
