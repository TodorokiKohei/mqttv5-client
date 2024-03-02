package org.eclipse.paho.sample.mqttv5app.sample;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.logging.Logger;
import org.eclipse.paho.mqttv5.client.logging.LoggerFactory;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.sample.mqttv5app.pingsender.StatusPingSender;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;

public class ShareSample {
	private static final String CLASS_NAME = ShareSample.class.getName();
	protected Logger log = LoggerFactory.getLogger(LoggerFactory.MQTT_CLIENT_MSG_CAT, CLASS_NAME);

	private int execTime;
	private int pubNum;
	private int subNum;

	static ObjectMapper mapper = new ObjectMapper();

	private CommonConfig config;

	static class CommonConfig {
		public String serverURI;
		public int keepAlive;
		public int qos;
		public String topic;

		public int publishInterval;
		public int pingInterval;
		public ArrayList<Integer> subProcessingTime;
		public int messageSize;
	}

	static class Payload {
		@JsonProperty("messageId")
		public int messageId;

		@JsonProperty("sendTime")
		public long sendTime;

		@JsonProperty("data")
		public String data;

		@JsonCreator
		public Payload(
				@JsonProperty("messageId") int messageId,
				@JsonProperty("sendTime") long sendTime,
				@JsonProperty("data") String data
		){
			this.messageId = messageId;
			this.sendTime = sendTime;
			this.data = data;
		}
	}

	public static void main(String[] args) throws ParseException {
		ShareSample ss = new ShareSample();
		ss.setup(args);
		ss.run();
	}

	public void setup(String[] args) throws ParseException {
		Options options = new Options();
		options.addOption("t", "exec-time", true, "execution time[sec]");
		options.addOption("p", "pub", true, "number of publisher");
		options.addOption("s", "sub", true, "number of subscriber");

		options.addOption("h", "host", true, "server URI");
		options.addOption("", "topic", true, "topic");
		options.addOption("k", "keep-alive", true, "keep alive");
		options.addOption("q", "qos", true, "qos");

		options.addOption("i", "pub-interval", true, "time interval between publishing message");

		options.addOption("", "ping-interval", true, "time interval between pingreq");
		options.addOption(Option.builder("")
				.longOpt("process-time")
				.hasArgs()
				.desc("subscriber processing time")
				.valueSeparator(',')
				.required()
				.build());

		options.addOption("", "size", true, "message size");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		this.execTime = Integer.parseInt(cmd.getOptionValue("t", "10")) * 1000;
		this.pubNum = Integer.parseInt(cmd.getOptionValue("p", "1"));
		this.subNum = Integer.parseInt(cmd.getOptionValue("s", "1"));

		CommonConfig config = new CommonConfig();
		config.serverURI = cmd.getOptionValue("host", "tcp://localhost:1883");
		config.topic = cmd.getOptionValue("topic", "t");
		config.qos = Integer.parseInt(cmd.getOptionValue("qos", "0"));
		config.keepAlive = Integer.parseInt(cmd.getOptionValue("keepAlive", "60"));
		config.publishInterval = Integer.parseInt(cmd.getOptionValue("pub-interval", "1000"));
		config.pingInterval = Integer.parseInt(cmd.getOptionValue("ping-interval", "1000"));

		String[] subProcessingTime = cmd.getOptionValues("process-time");
		if (subProcessingTime.length != this.subNum) {
			throw new ParseException("invalid subscriber processing time");
		}
		config.subProcessingTime = new ArrayList<>();
		for (String s : subProcessingTime) {
			config.subProcessingTime.add(Integer.parseInt(s));
		}
		config.messageSize = Integer.parseInt(cmd.getOptionValue("size", "100"));
		this.config = config;
	}

	public void run() {
		CountDownLatch latch = new CountDownLatch(pubNum + subNum);

		try {
			mapper.readValue("{\"messageId\": 0, \"sendTime\":0, \"data\":\"data\"}", Payload.class);
			mapper.writeValueAsString(new Payload(0, 0, ""));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

		// create subscriber
		ArrayList<SubBG> subList = new ArrayList<>();
		for (int i = 0; i < subNum; i++) {
			try {
				StatusPingSender pingSender = new StatusPingSender();
				pingSender.setPingIntervalMilliSeconds(config.pingInterval);
				SubBG sub = new SubBG(config,
						"sub-" + Integer.valueOf(i).toString(),
						latch,
						pingSender,
						config.subProcessingTime.get(i));
				subList.add(sub);
				sub.connect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
		}

		// create publisher
		ArrayList<PubBG> pubList = new ArrayList<>();
		for (int i = 0; i < pubNum; i++) {
			try {
				PubBG pub = new PubBG(config,
						"pub-" + Integer.valueOf(i).toString(),
						latch,
						config.messageSize);
				pubList.add(pub);
				pub.connect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
			Path dirPath = Paths.get("results/" + ldt.format(dtf));
			Files.createDirectories(dirPath);

			for (SubBG sub : subList) sub.start(dirPath);
			for (PubBG pub : pubList) pub.start();
			log.info(CLASS_NAME, "run", "Wait: " + execTime + " [msec]");
			Thread.sleep(execTime);
			pubList.forEach(c -> c.setRunning(false));
			subList.forEach(c -> c.setRunning(false));
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (MqttException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}



	abstract class clientBG {
		private final String CLASS_NAME = clientBG.class.getName();

		protected CommonConfig config;
		protected String clientId;

		protected MqttAsyncClient client;
		private boolean isRunning;

//		protected ObjectMapper mapper;

		public clientBG(CommonConfig config, String clientId, MqttPingSender pingSender) throws MqttException {
			this.config = config;
			this.clientId = clientId;

			MemoryPersistence persistence = new MemoryPersistence();
			client = new MqttAsyncClient(config.serverURI, clientId, persistence, pingSender, null);

//			mapper = new ObjectMapper();
		}

		public void connect() throws MqttException {
			MqttConnectionOptions conOpts = new MqttConnectionOptions();
			conOpts.setKeepAliveInterval(config.keepAlive);
			IMqttToken mt = client.connect(conOpts);
			mt.waitForCompletion();
			log.info(CLASS_NAME, "connect",this.clientId + " is connected");
		}

		public void setRunning(boolean running) {
			isRunning = running;
		}

		public boolean isRunning() {
			return isRunning;
		}
	}


	class PubBG extends clientBG implements Runnable {
		private final String CLASS_NAME = PubBG.class.getName();

		private CountDownLatch latch;
		private String data;

		public PubBG(CommonConfig config, String clientId, CountDownLatch latch, int messageSize) throws MqttException {
			super(config, clientId, null);
			this.latch = latch;
			this.data =  "X".repeat(messageSize);
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
					long sendTime = Instant.now().toEpochMilli();
					Payload payload = new Payload(msgId, sendTime, data);
					MqttMessage msg = new MqttMessage(mapper.writeValueAsString(payload).getBytes());
					msg.setQos(config.qos);
					client.publish(config.topic, msg);
					msgId++;
					Thread.sleep(config.publishInterval);
				} catch (InterruptedException | MqttException e) {
					throw new RuntimeException(e);
				} catch (JsonProcessingException e) {
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
		private final String CLASS_NAME = SubBG.class.getName();

		private final CountDownLatch latch;
		private final List<String> results;
		private Path dirPath;
		private final StatusPingSender pingSender;
		private final int processingTime;

		public SubBG(CommonConfig config, String clientId, CountDownLatch latch, StatusPingSender pingSender, int processingTime) throws MqttException {
			super(config, clientId, pingSender);
			this.latch = latch;
			this.results = new ArrayList<String>();
			this.pingSender = pingSender;
			this.processingTime = processingTime;
			this.client.resizeReceiverQueueSize(1000);
			client.setCallback(this);
		}

		public void start(Path dirPath) throws MqttException {
			log.info(CLASS_NAME, "start",clientId + ":" + processingTime + " [msec]");
			this.dirPath = dirPath;
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

			while (client.getNumberOfMsgsUnprocessed() != 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			try {
				client.disconnect();
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}

			// write results
			try (BufferedWriter bw =
						 Files.newBufferedWriter(dirPath.resolve(clientId + ".csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				bw.write("clientId,messageId,sendTime,receivedTime,latency");
				bw.newLine();
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
			String methodName = "messageArrived";

			long receivedTime = Instant.now().toEpochMilli();
			Payload payload;
			try {
				payload = mapper.readValue(message.getPayload(), Payload.class);
			} catch (Exception e) {
				log.warning(CLASS_NAME, methodName, e.getMessage());
				throw e;
			}
			long latency = receivedTime -payload.sendTime;

			StringJoiner sj = new StringJoiner(",");
			sj.add(clientId);
			sj.add(String.valueOf(payload.messageId));
			sj.add(String.valueOf(payload.sendTime));
			sj.add(String.valueOf(receivedTime));
			sj.add(String.valueOf(latency));

			results.add(sj.toString());
			Thread.sleep(processingTime);
			pingSender.updateProcessingTimePerMsg(Instant.now().toEpochMilli()-receivedTime);
//			pingSender.updateProcessingTimePerMsg(processingTime);
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
