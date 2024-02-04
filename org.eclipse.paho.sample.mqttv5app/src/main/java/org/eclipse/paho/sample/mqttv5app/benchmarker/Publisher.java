package org.eclipse.paho.sample.mqttv5app.benchmarker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;

import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.logging.Level;


public class Publisher implements Client, Runnable, MqttCallback {

	private BenchmarkOptions opts;
	private String clientId;
	private String startTopic;
	private CountDownLatch latch;

	private ObjectMapper mapper;
	private long publishInterval;
	private long publishCount;

	private MqttAsyncClient client;
	private ScheduledExecutorService service;
	private ScheduledFuture<?> future;

	private String messageData;
	private volatile boolean isTerminate = false;

	public Publisher(BenchmarkOptions opts, String clientId) {
		this(opts, clientId, "", null);
	}

	public Publisher(BenchmarkOptions opts, String clientId, String startTopic, CountDownLatch latch) {
		this.opts = opts;
		this.clientId = clientId;
		this.startTopic = startTopic;
		this.latch = latch;

		this.mapper = new ObjectMapper();
		this.publishInterval = opts.getPublishInterval(TimeUnit.MICROSECONDS);
		this.publishCount = 0;
	}

	public String getClientId() {
		return clientId;
	}

	public void start() throws MqttException {
		// 接続
		MemoryPersistence persistence = new MemoryPersistence();
		client = new MqttAsyncClient(opts.brokerUrl, clientId, persistence);
		MqttConnectionOptions conOpts = new MqttConnectionOptions();
		IMqttToken mt = client.connect(conOpts);
		mt.waitForCompletion();

		// startTopicが指定されている場合は、startTopicをsubscribe
		if (!startTopic.equals("")) {
            client.setCallback(this);
            client.subscribe(startTopic, opts.qos);
			isTerminate = true;		// publishを待機状態にする
        }

		// 送信レートの設定の有無でスケジューラの設定を変える
		service = Executors.newSingleThreadScheduledExecutor();
		if (publishInterval > 0) {
			// 定期的にタスクが実行されるようにスケジューラを設定
			Benchmarker.logger.log(Level.INFO, "{0} start publishing with interval {1} micro seconds", new Object[]{clientId, publishInterval});
			future = service.scheduleAtFixedRate(this, 0, publishInterval, TimeUnit.MICROSECONDS);
		} else {
			// 一度だけタスクが実行されるようにスケジューラを設定
			Benchmarker.logger.log(Level.INFO, "{0} start publishing without interval", new Object[]{clientId});
			future = service.schedule(this, 0, TimeUnit.SECONDS);
		}
	}

	public void stop() throws MqttException, InterruptedException {
		// スレッド停止
		if (!future.isDone()) {
			isTerminate = true;
			future.cancel(false);
		}
		if (service != null) {
			service.shutdown();
			service.awaitTermination(5, TimeUnit.SECONDS);
		}

		// 切断
		if (client != null) {
			client.disconnect();
		}
		Benchmarker.logger.log(Level.INFO, "{0} stop publishing", new Object[]{clientId});
	}

	public void run() {
		if (publishInterval > 0) {
			publishOnce();
		} else {
			publishConsecutively();
		}
	}

	private void publishOnce() {
		if (!isTerminate) {
			publish();
		}
	}

	private void publishConsecutively() {
		while (!isTerminate) {
			publish();
		}
	}

	private void publish() {
		Payload payload = createPayload();
		try {
			MqttMessage msg = new MqttMessage(mapper.writeValueAsBytes(payload));
			msg.setQos(opts.qos);
			IMqttToken mt = client.publish(opts.topic, msg);
			mt.waitForCompletion();
		} catch (JsonProcessingException | MqttException e) {
			Benchmarker.logger.log(Level.SEVERE, clientId + " failed to publish message.", e);
			throw new RuntimeException(e);
		}
		publishCount++;
	}

	private Payload createPayload() {
		long now = Instant.now().toEpochMilli();
		Payload payload = new Payload(clientId, publishCount, now);
		setMessageData(payload);
		return payload;
	}

	private void setMessageData(Payload payload) {
		if (messageData == null) {
			try {
				String json = mapper.writeValueAsString(payload);
				if (opts.messageSize - json.length() < 0) {
					messageData = "";
				} else {
					messageData = RandomStringUtils.randomAscii(opts.messageSize - json.length());
				}
			} catch (Exception e) {
				Benchmarker.logger.log(Level.SEVERE, "Failed to create message data.", e);
			}
		}
		payload.data = messageData;
	}

	@Override
	public void disconnected(MqttDisconnectResponse disconnectResponse) {
	}

	@Override
	public void mqttErrorOccurred(MqttException exception) {
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		// startTopicを受信したらlatchをカウントダウンしてpublishを開始
		if (topic.equals(startTopic)) {
			latch.countDown();
			isTerminate = false;
		}
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
