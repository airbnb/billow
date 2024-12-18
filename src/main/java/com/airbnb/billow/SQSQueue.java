package com.airbnb.billow;

import lombok.Getter;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.util.Map;

@JsonFilter(SQSQueue.QUEUE_FILTER)
public class SQSQueue {
    public static final String QUEUE_FILTER = "QueueFilter";

    public static final String ATTR_APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED = "ApproximateNumberOfMessagesDelayed";
    public static final String ATTR_RECEIVE_MESSAGE_WAIT_TIME_SECONDS = "ReceiveMessageWaitTimeSeconds";
    public static final String ATTR_CREATED_TIMESTAMP = "CreatedTimestamp";
    public static final String ATTR_DELAY_SECONDS = "DelaySeconds";
    public static final String ATTR_MESSAGE_RETENTION_PERIOD = "MessageRetentionPeriod";
    public static final String ATTR_MAXIMUM_MESSAGE_SIZE = "MaximumMessageSize";
    public static final String ATTR_VISIBILITY_TIMEOUT = "VisibilityTimeout";
    public static final String ATTR_APPROXIMATE_NUMBER_OF_MESSAGES = "ApproximateNumberOfMessages";
    public static final String ATTR_LAST_MODIFIED_TIMESTAMP = "LastModifiedTimestamp";
    public static final String ATTR_QUEUE_ARN = "QueueArn";

    @Getter
    private final String url;
    @Getter
    private final Long approximateNumberOfMessagesDelayed;
    @Getter
    private final Long receiveMessageWaitTimeSeconds;
    @Getter
    private final Long createdTimestamp;
    @Getter
    private final Long delaySeconds;
    @Getter
    private final Long messageRetentionPeriod;
    @Getter
    private final Long maximumMessageSize;
    @Getter
    private final Long visibilityTimeout;
    @Getter
    private final Long approximateNumberOfMessages;
    @Getter
    private final Long lastModifiedTimestamp;
    @Getter
    private final String queueArn;
    @Getter
    private final Map<String, String> tags;



    public SQSQueue(String url,
                    Long approximateNumberOfMessagesDelayed,
                    Long receiveMessageWaitTimeSeconds,
                    Long createdTimestamp,
                    Long delaySeconds,
                    Long messageRetentionPeriod,
                    Long maximumMessageSize,
                    Long visibilityTimeout,
                    Long approximateNumberOfMessages,
                    Long lastModifiedTimestamp,
                    String queueArn,
                    Map<String, String> tags) {
        this.url = url;
        this.approximateNumberOfMessagesDelayed = approximateNumberOfMessagesDelayed;
        this.receiveMessageWaitTimeSeconds = receiveMessageWaitTimeSeconds;
        this.createdTimestamp = createdTimestamp;
        this.delaySeconds = delaySeconds;
        this.messageRetentionPeriod = messageRetentionPeriod;
        this.maximumMessageSize = maximumMessageSize;
        this.visibilityTimeout = visibilityTimeout;
        this.approximateNumberOfMessages = approximateNumberOfMessages;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
        this.queueArn = queueArn;
        this.tags = tags;
    }
}
