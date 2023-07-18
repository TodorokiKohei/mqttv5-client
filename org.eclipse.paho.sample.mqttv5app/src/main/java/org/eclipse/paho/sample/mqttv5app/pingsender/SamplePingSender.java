package org.eclipse.paho.sample.mqttv5app.pingsender;

import org.eclipse.paho.mqttv5.client.ExtendedPingSender;
import org.eclipse.paho.mqttv5.common.packet.MqttPingReq;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;

import java.util.ArrayList;

public class SamplePingSender extends ExtendedPingSender {

	private String payload;

	public SamplePingSender(String payload){
		this.payload = payload;
	}

	@Override
	protected MqttPingReq createPingreq() {
		MqttProperties properties = new MqttProperties();
		ArrayList<UserProperty> userProperties = new ArrayList<>();
		userProperties.add(new UserProperty("message_id", "1"));
		properties.setUserProperties(userProperties);
		MqttPingReq pingReq = new MqttPingReq(payload.getBytes());
		return pingReq;
	}
}
