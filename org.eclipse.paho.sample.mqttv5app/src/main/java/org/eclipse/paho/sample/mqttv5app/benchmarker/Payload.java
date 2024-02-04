package org.eclipse.paho.sample.mqttv5app.benchmarker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Payload {
	@JsonProperty("clientId")
	public String clientId;

	@JsonProperty("messageId")
	public long messageId;

	@JsonProperty("sendTime")
	public long sendTime;

	@JsonProperty("data")
	public String data;

	@JsonCreator
	public Payload(
			@JsonProperty("clientId") String clientId,
			@JsonProperty("messageId") long messageId,
			@JsonProperty("sendTime") long sendTime,
			@JsonProperty("data") String data
	) {
		this.clientId = clientId;
		this.messageId = messageId;
		this.sendTime = sendTime;
		this.data = data;
	}

	public Payload(String clientId, long messageId, long sendTime) {
		this(clientId, messageId, sendTime, "");
	}
}
