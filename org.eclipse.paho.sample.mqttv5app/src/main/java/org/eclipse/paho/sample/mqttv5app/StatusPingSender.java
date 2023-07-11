package org.eclipse.paho.sample.mqttv5app;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.paho.mqttv5.client.ExtendedPingSender;
import org.eclipse.paho.mqttv5.client.logging.Logger;
import org.eclipse.paho.mqttv5.client.logging.LoggerFactory;
import org.eclipse.paho.mqttv5.common.packet.MqttPingReq;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StatusPingSender extends ExtendedPingSender {
	private static final String CLASS_NAME = StatusPingSender.class.getName();

	private int bufferSize;
	private ArrayList<Double> processingTimeBuffer;
	private double processingTimePerMsg;

	private ObjectMapper mapper;

	public StatusPingSender(int bufferSize) {
		this.bufferSize = bufferSize;
		processingTimeBuffer = new ArrayList<>(bufferSize);
		processingTimePerMsg = 0;
		mapper = new ObjectMapper();
	}

	public void updateProcessingTimePerMsg(double processingTime){
		final String methodName = "updateProcessingTimePerMsg";

		double totalProcessingTime = processingTimePerMsg * processingTimeBuffer.size();
		if (processingTimeBuffer.size() == bufferSize) {
			double element = processingTimeBuffer.remove(0);
			totalProcessingTime -= element;
			log.info(CLASS_NAME, methodName, "Buffer is full so removed first element({0}).", new Object[]{element});
		}
		totalProcessingTime += processingTime;
		processingTimeBuffer.add(new Double(processingTime));
		log.info(CLASS_NAME, methodName, "Added {0} to the end of buffer.", new Object[]{processingTime});

		processingTimePerMsg = totalProcessingTime / processingTimeBuffer.size();
	}

	@Override
	protected MqttPingReq createPingreq() {
		final String methodName = "cratePingreq";

		Payload payload = new Payload(comms.getNumberOfMsgsUnprocessed(), processingTimePerMsg);
		try {
			String jsonPayload = mapper.writeValueAsString(payload);
			MqttPingReq pingReq = new MqttPingReq(jsonPayload.getBytes());
			log.info(CLASS_NAME, methodName, "Create PINGREQ payload: {0}.", new Object[]{jsonPayload});
			return pingReq;
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
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
