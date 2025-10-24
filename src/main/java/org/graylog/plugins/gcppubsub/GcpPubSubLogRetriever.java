package org.graylog.plugins.gcppubsub;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.shared.SuppressForbidden;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;

public class GcpPubSubLogRetriever {

  private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubLogRetriever.class);

  public static final int    BATCH_SIZE       = 1000;
  public static final int    MAX_MESSAGE_SIZE = 1024 * 1024 * 10;
  public static final int    NAP_TIME         = 50;

  private Configuration configuration;
  private MessageInput messageInput;
  private ExecutorService pool;
  private GrpcSubscriberStub subscriber;
  private volatile boolean shouldStop = false;

  @SuppressForbidden("Deliberate invocation")
  public GcpPubSubLogRetriever(Configuration configuration, MessageInput input) {
    this.configuration = configuration;
    this.pool = Executors.newCachedThreadPool();
    this.messageInput = input;
  }

  public String getProjectId() {
    String pidFromConfig = configuration.getString("gcp_project");
    if (pidFromConfig != null) {
      return pidFromConfig;
    }
    return "my-gcp-project";
  }

  public String getSubscriptionId() {
    String sidFromConfig = configuration.getString("subscription");
    if (sidFromConfig != null) {
      return sidFromConfig;
    }
    return "graylog-pull-subscription";
  }

  public String getSubscriptionName() {
    return ProjectSubscriptionName.format(getProjectId(), getSubscriptionId());
  }

  public void initSubscriber() throws FileNotFoundException, IOException {
    if (subscriber == null) {
      GoogleCredentials credentials;
      String keyFilePath = configuration.getString("service_account_key_file");

      if (keyFilePath != null && !keyFilePath.trim().isEmpty()) {
        // Use explicitly configured service account key file
        LOG.info("Using service account credentials from: {}", keyFilePath);
        credentials = ServiceAccountCredentials.fromStream(new FileInputStream(keyFilePath));
      } else {
        // Use Application Default Credentials (ADC)
        LOG.info("Using Application Default Credentials (no service_account_key_file configured)");
        credentials = GoogleCredentials.getApplicationDefault();
      }

      TransportChannelProvider tcp = SubscriberStubSettings.defaultGrpcTransportProviderBuilder().setMaxInboundMessageSize(MAX_MESSAGE_SIZE).setCredentials(credentials).build();
      SubscriberStubSettings subscriberStubSettings = SubscriberStubSettings.newBuilder().setTransportChannelProvider(tcp).build();
      subscriber = GrpcSubscriberStub.create(subscriberStubSettings);
    }
  }

  public int processBatch() throws FileNotFoundException, IOException {

    initSubscriber();

    GcpPubSubTopicReader topicReader = new GcpPubSubTopicReader(getSubscriptionName(), subscriber);
    List<ReceivedMessage> messages = topicReader.rx();

    if (messages == null) {
      LOG.error("no messages were retrieved");
      return 0;
    }

    int messageCount = messages.size();
    CountDownLatch latch = new CountDownLatch(messageCount);
    List<String> ackIds = new ArrayList<>();
    List<String> nackIds = new ArrayList<>();

    for (ReceivedMessage message : messages) {
      ackIds.add(message.getAckId());
      pool.submit(new GcpPubSubLogRetrieverThread(message, latch, nackIds, messageInput));
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      LOG.error("Interrupted while waiting for batch processing to complete", e);
      Thread.currentThread().interrupt();
    }

    nackIds.removeAll(ackIds);
    topicReader.ack(ackIds);
    topicReader.nack(nackIds);

    return messageCount;
  }

  @SuppressForbidden("Intentionally use system default timezone")
  public void processBatches() {

    long processedBatchCount = 1;
    long tally = 0;

    while (!shouldStop && !pool.isTerminated()) {
      try {

        long t0 = System.currentTimeMillis();
        int count = processBatch();
        long t1 = System.currentTimeMillis();
        long msToProcess = t1 - t0;

        tally += msToProcess;

        double average = (double) tally / processedBatchCount;
        String format = "\"%s\",\"%s\",\"%s\",\"%s\",\"%.2f\",%s";
        LOG.debug(String.format(format, processedBatchCount, count, BATCH_SIZE, msToProcess, average, mem()));

        if (processedBatchCount > Long.MAX_VALUE-2) processedBatchCount = 0;
        ++processedBatchCount;

        Thread.sleep(NAP_TIME);

      } catch (Exception e) {
        LOG.error("GcpPubSubLogRetriever::processBatches: caught Exception ->" + e);
      }
    }
    LOG.warn("Executor pool has been terminated unexpectedly");
    pool.shutdown();
  }

  public void start() {
    shouldStop = false;
    processBatches();
  }

  public void stop() {
    shouldStop = true;

    // Shutdown executor pool gracefully
    if (pool != null && !pool.isShutdown()) {
      pool.shutdown();
      try {
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
          LOG.warn("Executor pool did not terminate in time, forcing shutdown");
          pool.shutdownNow();
        }
      } catch (InterruptedException e) {
        LOG.warn("Interrupted while waiting for executor pool to terminate", e);
        pool.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Close GCP Pub/Sub subscriber
    if (subscriber != null) {
      try {
        subscriber.close();
        LOG.info("GCP Pub/Sub subscriber closed successfully");
      } catch (Exception e) {
        LOG.error("Error closing GCP Pub/Sub subscriber", e);
      }
    }
  }

  @SuppressForbidden("Intentionally use system default timezone")
  public String mem() {
    long freeMem  = Runtime.getRuntime().freeMemory();
    long maxMem   = Runtime.getRuntime().maxMemory();
    long totalMem = Runtime.getRuntime().totalMemory();
    long usedMem  = totalMem - freeMem;
    long usedPercentage = usedMem * 100 / totalMem;

    String format = "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"";
    String info = String.format(format, usedMem, freeMem, totalMem, maxMem, usedPercentage);

    return info;
  }


  public static class GcpPubSubLogRetrieverThread implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubLogRetrieverThread.class);
    private static final int MAX_PAYLOAD_LENGTH = 32767;
    private static final int TRUNCATED_PAYLOAD_LENGTH = 1024;

    private final int tid = hashCode();
    private ReceivedMessage receivedMessage;
    private CountDownLatch countdownLatch;
    private List<String> nackIds;
    private MessageInput messageInput;
    private PubsubMessage pubsubMessage;
    private ByteString data;

    public GcpPubSubLogRetrieverThread(ReceivedMessage message, CountDownLatch latch, List<String> nackIds, MessageInput messageInput) {
      this.receivedMessage = message;
      this.countdownLatch = latch;
      this.nackIds = nackIds;
      this.messageInput = messageInput;
    }

    public JSONObject sanitizeKeys(JSONObject json) {

      Set<String> jsonKeys = json.keySet();

      boolean ok = true;
      for (String key : jsonKeys) {
        if (key.matches(".*[/\\(\\)].*")) ok = false;
        break;
      }
      if (ok) return json;

      JSONObject clone= new JSONObject(json.toMap());

      for (String key : jsonKeys) {
        String newKey = key;
        if (key.matches(".*/.*"))   newKey = newKey.replaceAll("/", ".");
        if (key.matches(".*\\(.*")) newKey = newKey.replaceAll("[(]", "");
        if (key.matches(".*\\).*")) newKey = newKey.replaceAll("[)]", "");
        if (!key.equals(newKey)) {
          for (int attempts=0; attempts < 25; attempts++) {
            if (jsonKeys.contains(newKey)) {
              newKey += "." + Integer.toString(attempts);
              continue;
            }
          }
          clone.put(newKey, json.get(key));
          clone.remove(key);
        }
      }
      return clone;
    }

    public String sanitize(String strArg) {
      String str = strArg.replaceAll("/", "-");
      str = str.replaceAll("\\$", "");
      return str;
    }

    @Override
    public void run() {
      try {
        pubsubMessage = receivedMessage.getMessage();
        data = pubsubMessage.getData();

        JSONObject json = sanitizeKeys(new JSONObject(data.toStringUtf8()));
        Set<String> jsonKeys = json.keySet();

        if (jsonKeys.contains("textPayload")) {
          String textPayload = json.get("textPayload").toString();
          long mlen = textPayload.length();
          if (mlen > MAX_PAYLOAD_LENGTH) {
            json.put("textPayload", textPayload.substring(0, TRUNCATED_PAYLOAD_LENGTH) + " (truncated - original size was " + Long.toString(mlen) + " bytes)");
          }
        }

        if (jsonKeys.contains("jsonPayload")) {
          String jsonPayload = json.get("jsonPayload").toString();
          long mlen = jsonPayload.length();
          if (mlen > MAX_PAYLOAD_LENGTH) {
            json.put("jsonPayload", jsonPayload.substring(0, TRUNCATED_PAYLOAD_LENGTH) + " (truncated - original size was " + Long.toString(mlen) + " bytes)");
          }
        }

        messageInput.processRawMessage(new RawMessage(sanitize(json.toString()).getBytes("UTF-8")));

      } catch (Exception e) {

        LOG.error("GcpPubSubLogRetrieverThread: caught EXCEPTION ->" + e.getMessage());
        LOG.error(data.toStringUtf8());

        nackIds.add(receivedMessage.getAckId());

      } finally {
        countdownLatch.countDown();
      }

    }
  }

}
