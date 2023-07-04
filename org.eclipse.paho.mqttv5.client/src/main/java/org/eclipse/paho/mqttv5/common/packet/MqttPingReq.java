/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0
 * and the Eclipse Distribution License is available at 
 *   https://www.eclipse.org/org/documents/edl-v10.php
 *
 * Contributors:
 * 	  Dave Locke   - Original MQTTv3 implementation
 *    James Sutton - Initial MQTTv5 implementation
 */
package org.eclipse.paho.mqttv5.common.packet;

import org.eclipse.paho.mqttv5.common.MqttException;

public class MqttPingReq extends MqttWireMessage{

	public static final String KEY = "Ping";

	private static final Byte[] validProperties = {MqttProperties.USER_DEFINED_PAIR_IDENTIFIER};
	private MqttProperties properties;

	private byte[] payload;

	public MqttPingReq(){
		this(null, null);
	}


	public MqttPingReq(byte[] payload) {
		this(payload, null);
	}

	public MqttPingReq(byte[] payload, MqttProperties properties) {
		super(MqttWireMessage.MESSAGE_TYPE_PINGREQ);
		if (properties != null) {
			this.properties = properties;
		} else {
			// [MQTT-2.2.2-1] プロパティのサイズは必ず含まなければならない
			this.properties = new MqttProperties();
		}
		this.properties.setValidProperties(validProperties);
		this.payload = payload;
	}

	/**
	 * Returns <code>false</code> as message IDs are not required for MQTT
	 * PINGREQ messages.
	 */
	@Override
	public boolean isMessageIdRequired() {
		return false;
	}

	@Override
	protected byte[] getVariableHeader() throws MqttException {
		return this.properties.encodeProperties();
	}
	
	@Override
	protected byte getMessageInfo() {
		return 0;
	}
	
	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public byte[] getPayload(){
		if (this.payload == null) return new byte[] {};
		return this.payload;
	}
}
