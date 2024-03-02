package org.eclipse.paho.sample.mqttv5app.benchmarker;

public class Record {
	private String sentClientId;
	private String receivedClientId;
	private long messageId;
	private long sentTime;
	private long receivedTime;
	private long latency;

	private boolean isLast = false;

	public Record(String sentClientId, String receivedClientId, long messageId, long sentTime, long receivedTime) {
		this.sentClientId = sentClientId;
		this.receivedClientId = receivedClientId;
		this.messageId = messageId;
		this.sentTime = sentTime;
		this.receivedTime = receivedTime;
		this.latency = receivedTime - sentTime;
	}

	public Record(boolean isLast) {
		this.isLast = isLast;
	}

	public boolean isLast() {
		return isLast;
	}

	public static String toCsvHeader() {
		String[] values = {"sentClientId", "receivedClientId", "messageId", "sentTime", "receivedTime", "latency"};
		return String.join(",", values);
	}

	public String toCsvRow() {
		String[] values = {sentClientId, receivedClientId, String.valueOf(messageId), String.valueOf(sentTime), String.valueOf(receivedTime), String.valueOf(latency)};
		return String.join(",", values);
	}


}
