package org.graylog.plugins.gcppubsub;

import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.graylog2.shared.SuppressForbidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpPubSubTopicReader implements Callable<PullRequest> {

  private static final Logger LOG = LoggerFactory.getLogger(GcpPubSubTopicReader.class);

  private String projectId;
  private String subscriptionId;
  private String subscriptionName;
  private GrpcSubscriberStub subscriber;

  public GcpPubSubTopicReader(String subscriptionName, GrpcSubscriberStub subscriber) {
    this.subscriptionName = subscriptionName;
    this.subscriber = subscriber;
  }

  @Override
  public PullRequest call() throws Exception {
    return PullRequest.newBuilder().setMaxMessages(GcpPubSubLogRetriever.BATCH_SIZE).setSubscription(subscriptionName).build();
  }

  private List<String> ackIdsFromMessages(List<ReceivedMessage> messageList) {
    List<String> ackIds = new ArrayList<>();
    for (ReceivedMessage message : messageList) { ackIds.add(message.getAckId()); }
    return ackIds;
  }

  @SuppressForbidden("Deliberate invocation")
  private PullRequest buildPullRequest() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<PullRequest> future = executor.submit(this);
    PullRequest pullRequest = null;
    try {
      pullRequest = future.get(19, TimeUnit.SECONDS);
    } catch (TimeoutException te) {
      future.cancel(true);
    } catch (InterruptedException ie) {
      future.cancel(true);
    } catch (Exception e) {
      future.cancel(true);
    }
    return pullRequest;
  }

  public List<ReceivedMessage> rx() {
    PullRequest pullRequest = buildPullRequest();
    if (pullRequest == null) return new ArrayList<>();
    PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
    List<ReceivedMessage> messageList = pullResponse.getReceivedMessagesList();
    List<String> ackIds = ackIdsFromMessages(messageList);
    ModifyAckDeadlineRequest modifyRequest = ModifyAckDeadlineRequest.newBuilder().setSubscription(subscriptionName).addAllAckIds(ackIds).setAckDeadlineSeconds(60).build();
    if (modifyRequest == null) {
      LOG.info("rx -> null ModifyAckDeadlineRequest");
    } else {
      subscriber.modifyAckDeadlineCallable().call(modifyRequest);
    }
    return messageList;
  }

  public void ack(List<String> ackIds) {
    if (ackIds == null || ackIds.isEmpty()) return;
    AcknowledgeRequest req = AcknowledgeRequest.newBuilder().setSubscription(subscriptionName).addAllAckIds(ackIds).build();
    subscriber.acknowledgeCallable().call(req);
  }

  public void nack(List<String> nackIds) {
    if (nackIds == null || nackIds.isEmpty()) return;
    LOG.error("GcpPubSubTopicReader::nack: nack ID count: " + nackIds.size());
    AcknowledgeRequest req = AcknowledgeRequest.newBuilder().setSubscription(subscriptionName).addAllAckIds(nackIds).build();
    if (req == null) {
      LOG.error("GcpPubSubTopicReader::nack: null AcknowledgeRequest");
      return;
    }
    subscriber.acknowledgeCallable().call(req);
  }

}
