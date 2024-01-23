package org.eclipse.paho.sample.mqttv5app.pingsender;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.paho.mqttv5.client.ExtendedPingSender;
import org.eclipse.paho.mqttv5.client.MqttPingSender;
import org.eclipse.paho.mqttv5.common.packet.MqttPingReq;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StatusPingSender extends ExtendedPingSender {
	private static final String CLASS_NAME = StatusPingSender.class.getName();

	private volatile int messageCount;
	private volatile double totalProcessingTime;

	private ObjectMapper mapper;

	public StatusPingSender() {
		messageCount = 0;
		totalProcessingTime = 0;
		mapper = new ObjectMapper();
	}

	public void updateProcessingTimePerMsg(double processingTime){
		final String methodName = "updateProcessingTimePerMsg";

		synchronized (this) {
			messageCount++;
			totalProcessingTime += processingTime;
		}
	}

	@Override
	protected MqttPingReq createPingreq() {
		final String methodName = "cratePingreq";

		Payload payload;
		if (messageCount == 0) {
			payload = new Payload(comms.getNumberOfMsgsUnprocessed(), 0);
		} else {
			payload = new Payload(comms.getNumberOfMsgsUnprocessed(), totalProcessingTime/messageCount);
		}
		MqttPingReq pingReq;
		try {
			String jsonPayload = mapper.writeValueAsString(payload);
			pingReq = new MqttPingReq(jsonPayload.getBytes());
			log.info(CLASS_NAME, methodName, "Create PINGREQ payload: {0}.", new Object[]{jsonPayload});
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		synchronized (this) {
			messageCount = 0;
			totalProcessingTime = 0;
		}
		return pingReq;
	}

	class Payload {
		@JsonProperty("numberOfMsgsInQueue")
		public int numberOfMsgsInQueue;

		@JsonProperty("processingTimerPerMsg")
		public double processingTimerPerMsg;

		public Payload(int numberOfMsgsInQueue, double processingTimerPerMsg){
			this.numberOfMsgsInQueue = numberOfMsgsInQueue;
			this.processingTimerPerMsg = processingTimerPerMsg;
		}
	}
}
