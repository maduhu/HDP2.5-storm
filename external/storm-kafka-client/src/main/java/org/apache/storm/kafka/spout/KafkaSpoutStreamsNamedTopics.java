/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.storm.kafka.spout;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.OutputFieldsGetter;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the {@link KafkaSpoutStream} associated with each topic, and provides a public API to
 * declare output streams and emmit tuples, on the appropriate stream, for all the topics specified.
 */
public class KafkaSpoutStreamsNamedTopics implements KafkaSpoutStreams {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaSpoutStreamsNamedTopics.class);

    private final Map<String, KafkaSpoutStream> topicToStream;

    private KafkaSpoutStreamsNamedTopics(Builder builder) {
        this.topicToStream = builder.topicToStream;
        LOG.debug("Built {}", this);
    }

    /**
     * @param topic the topic for which to get output fields
     * @return the declared output fields
     */
    public Fields getOutputFields(String topic) {
        if (topicToStream.containsKey(topic)) {
            final Fields outputFields = topicToStream.get(topic).getOutputFields();
            LOG.trace("Topic [{}] has output fields [{}]", topic, outputFields);
            return outputFields;
        }
        throw new IllegalStateException(this.getClass().getName() + " not configured for topic: " + topic);
    }

    /**
     * @param topic the topic to for which to get the stream id
     * @return the id of the stream to where the tuples are emitted
     */
    public KafkaSpoutStream getStream(String topic) {
        if (topicToStream.containsKey(topic)) {
            return topicToStream.get(topic);
        }
        throw new IllegalStateException(this.getClass().getName() + " not configured for topic: " + topic);
    }

    /**
     * @return list of topics subscribed and emitting tuples to a stream as configured by {@link KafkaSpoutStream}
     */
    public List<String> getTopics() {
        return new ArrayList<>(topicToStream.keySet());
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        for (KafkaSpoutStream stream : topicToStream.values()) {
            if (!((OutputFieldsGetter)declarer).getFieldsDeclaration().containsKey(stream.getStreamId())) {
                stream.declareOutputFields(declarer);
            }
        }
    }

    @Override
    public Fields getOutputFields() {
        final Set<String> uniqueFields = new HashSet<>();

        for (KafkaSpoutStream kafkaSpoutStream : topicToStream.values()) {
            uniqueFields.addAll(kafkaSpoutStream.getOutputFields().toList());
        }

        return new Fields(new ArrayList<>(uniqueFields));
    }

    public void emit(SpoutOutputCollector collector, List<Object> tuple, KafkaSpoutMessageId messageId) {
        getStream(messageId.topic()).emit(collector, tuple, messageId);
    }

    @Override
    public String toString() {
        return "KafkaSpoutStreamsNamedTopics{" +
                "topicToStream=" + topicToStream +
                '}';
    }

    public static class Builder {
        private final Map<String, KafkaSpoutStream> topicToStream = new HashMap<>();;

        /**
         * Creates a {@link KafkaSpoutStream} with the given output Fields for each topic specified.
         * All topics will have the default stream id and the same output fields.
         */
        public Builder(Fields outputFields, String... topics) {
            addStream(outputFields, topics);
        }

        /**
         * Creates a {@link KafkaSpoutStream} with this particular stream for each topic specified.
         * All the topics will have the specified stream id and the same output fields.
         */
        public Builder (Fields outputFields, String streamId, String... topics) {
            addStream(outputFields, streamId, topics);
        }

        /**
         * Adds this stream to the state representing the streams associated with each topic
         */
        public Builder(KafkaSpoutStream stream) {
            addStream(stream);
        }

        /**
         * Adds this stream to the state representing the streams associated with each topic
         */
        public Builder addStream(KafkaSpoutStream stream) {
            topicToStream.put(stream.getTopic(), stream);
            return this;
        }

        /**
         * Please refer to javadoc in {@link #Builder(Fields, String...)}
         */
        public Builder addStream(Fields outputFields, String... topics) {
            addStream(outputFields, Utils.DEFAULT_STREAM_ID, topics);
            return this;
        }

        /**
         * Please refer to javadoc in {@link #Builder(Fields, String, String...)}
         */
        public Builder addStream(Fields outputFields, String streamId, String... topics) {
            for (String topic : topics) {
                topicToStream.put(topic, new KafkaSpoutStream(outputFields, streamId, topic));
            }
            return this;
        }

        public KafkaSpoutStreamsNamedTopics build() {
            return new KafkaSpoutStreamsNamedTopics(this);
        }
    }
}
