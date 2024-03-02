package org.eclipse.paho.sample.mqttv5app.benchmarker;

import org.eclipse.paho.mqttv5.common.MqttException;

public interface Client {
	public String getClientId();
	public void start() throws MqttException;
	public void stop() throws MqttException, InterruptedException;
}
