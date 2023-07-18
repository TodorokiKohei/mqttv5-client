package org.eclipse.paho.sample.mqttv5app;

import org.apache.commons.cli.*;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ShareSample {

	private int execTime;
	private int pubNum;
	private int subNum;

	private CommonConfig config;

	public static void main(String[] args) throws ParseException {
		ShareSample ss = new ShareSample();
		ss.setup(args);
		ss.run();
	}

	public void setup(String[] args) throws ParseException {
		Options options = new Options();
		options.addOption("t", "time", true, "execution time[msec]");
		options.addOption("p", "pub", true, "number of publisher");
		options.addOption("s", "sub", true, "number of subscriber");
		options.addOption("h", "host", true, "server URI");
		options.addOption("", "topic", true, "topic");
		options.addOption("k", "keep-alive", true, "keep alive");
		options.addOption("q", "qos", true, "qos");
		options.addOption("i", "interval", true, "time interval between publishing message");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		this.execTime = Integer.parseInt(cmd.getOptionValue("t", "10000"));
		this.pubNum = Integer.parseInt(cmd.getOptionValue("p", "1"));
		this.subNum = Integer.parseInt(cmd.getOptionValue("s", "1"));

		CommonConfig config = new CommonConfig();
		config.serverURI = cmd.getOptionValue("host", "tcp://localhost:1883");
		config.topic = cmd.getOptionValue("topic", "t");
		config.qos = Integer.parseInt(cmd.getOptionValue("qos", "0"));
		config.keepAlive = Integer.parseInt(cmd.getOptionValue("keepAlive", "60"));
		config.publishInterval = Integer.parseInt(cmd.getOptionValue("interval", "1000"));
		this.config = config;
	}

	public void run() {
		CountDownLatch latch = new CountDownLatch(pubNum + subNum);

		ArrayList<SubBG> subList = new ArrayList<>();
		for (int i = 0; i < subNum; i++) {
			try {
				SubBG sub = new SubBG(config, "sub-" + Integer.valueOf(i).toString(), latch);
				subList.add(sub);
				sub.connect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
		}

		ArrayList<PubBG> pubList = new ArrayList<>();
		for (int i = 0; i < pubNum; i++) {
			try {
				PubBG pub = new PubBG(config, "pub-" + Integer.valueOf(i).toString(), latch);
				pubList.add(pub);
				pub.connect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			long startTime = Instant.now().toEpochMilli();
			for (SubBG sub : subList) sub.start(startTime);
			for (PubBG pub : pubList) pub.start();
			System.out.println("Wait: " + execTime + " [msec]");
			Thread.sleep(execTime / 2);
			Thread.sleep(execTime / 2);
			pubList.forEach(c -> c.setRunning(false));
			subList.forEach(c -> c.setRunning(false));
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (MqttException e) {
			throw new RuntimeException(e);
		}

	}

	static class CommonConfig {
		public String serverURI;
		public int keepAlive;
		public int qos;
		public String topic;

		public int publishInterval;
	}

	abstract class clientBG {

		protected CommonConfig config;
		protected String clientId;

		protected MqttAsyncClient client;
		private boolean isRunning;

		public clientBG(CommonConfig config, String clientId) throws MqttException {
			this.config = config;
			this.clientId = clientId;

			MemoryPersistence persistence = new MemoryPersistence();
			client = new MqttAsyncClient(config.serverURI, clientId, persistence);
		}

		public void connect() throws MqttException {
			MqttConnectionOptions conOpts = new MqttConnectionOptions();
			conOpts.setKeepAliveInterval(config.keepAlive);
			IMqttToken mt = client.connect(conOpts);
			mt.waitForCompletion();
			System.out.println(this.clientId + " is connected");
		}

		public void setRunning(boolean running) {
			isRunning = running;
		}

		public boolean isRunning() {
			return isRunning;
		}
	}


	class PubBG extends clientBG implements Runnable {

		private CountDownLatch latch;

		public PubBG(CommonConfig config, String clientId, CountDownLatch latch) throws MqttException {
			super(config, clientId);
			this.latch = latch;
		}

		public void start() throws MqttException {
			new Thread(this).start();
		}

		@Override
		public void run() {
			setRunning(true);
			int msgId = 0;
			while (isRunning()) {
				try {
					MqttMessage msg = new MqttMessage(("num:" + msgId).getBytes());
					msg.setQos(config.qos);
					client.publish(config.topic, msg);
					msgId++;
					Thread.sleep(config.publishInterval);
				} catch (InterruptedException | MqttException e) {
					throw new RuntimeException(e);
				}
			}
			try {
				client.disconnect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
			latch.countDown();
		}
	}

	class SubBG extends clientBG implements Runnable, MqttCallback {

		private final CountDownLatch latch;
		private final List<String> results;
		private long startTime;

		public SubBG(CommonConfig config, String clientId, CountDownLatch latch) throws MqttException {
			super(config, clientId);
			this.latch = latch;
			this.results = new ArrayList<String>();
			client.setCallback(this);
		}

		public void start(long startTime) throws MqttException {
			this.startTime = startTime;
			client.subscribe("$share/g/" + config.topic, config.qos);
			new Thread(this).start();
		}

		@Override
		public void run() {
			setRunning(true);
			while (isRunning()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			try {
				client.disconnect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
			try (BufferedWriter bw =
						 Files.newBufferedWriter(Paths.get("results/" + clientId + ".csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				for (String res : results) {
					bw.write(res);
					bw.newLine();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			latch.countDown();
		}

		@Override
		public void disconnected(MqttDisconnectResponse disconnectResponse) {
		}

		@Override
		public void mqttErrorOccurred(MqttException exception) {
		}

		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception {
			long diff = Instant.now().toEpochMilli() - startTime;
			results.add(diff + "," + new String(message.getPayload()));
		}

		@Override
		public void deliveryComplete(IMqttToken token) {
		}

		@Override
		public void connectComplete(boolean reconnect, String serverURI) {
		}

		@Override
		public void authPacketArrived(int reasonCode, MqttProperties properties) {
		}
	}
}
