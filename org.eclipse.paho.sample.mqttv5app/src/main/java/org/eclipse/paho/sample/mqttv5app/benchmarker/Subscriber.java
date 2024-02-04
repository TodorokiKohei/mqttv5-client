package org.eclipse.paho.sample.mqttv5app.benchmarker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.sample.mqttv5app.pingsender.StatusPingSender;

import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.logging.Level;

public class Subscriber implements Client, Runnable, MqttCallback {

	private BenchmarkOptions opts;
	private String clientId;
	private BufferedWriter bw;
	private ObjectMapper mapper;

	private MqttAsyncClient client;
	private ExtendedPingSender pingSender = null;
	private ScheduledExecutorService service;
	private ScheduledFuture<?> future;

	private ArrayBlockingQueue<Record> queue = new ArrayBlockingQueue<>(1000000);

	private volatile boolean isTerminate = false;

	public Subscriber(BenchmarkOptions opts, String clientId, BufferedWriter bw) {
		this.opts = opts;
		this.clientId = clientId;
		this.bw = bw;

		this.mapper = new ObjectMapper();
	}

	public String getClientId() {
		return clientId;
	}

	public void start() throws MqttException {
		// 接続
		MemoryPersistence persistence = new MemoryPersistence();
		if (opts.enableExtendedPingSender) {
			pingSender = new StatusPingSender();
			pingSender.setPingIntervalMilliSeconds(1000);
			client = new MqttAsyncClient(opts.brokerUrl, clientId, persistence, pingSender, null);
			client.resizeReceiverQueueSize(1000);
			Benchmarker.logger.log(Level.INFO, "{0} is using extended ping sender", new Object[]{clientId});
		} else {
			client = new MqttAsyncClient(opts.brokerUrl, clientId, persistence);
		}
		client.setCallback(this);
		MqttConnectionOptions connOpts = new MqttConnectionOptions();
		connOpts.setKeepAliveInterval(60);
		IMqttToken mt = client.connect(connOpts);
		mt.waitForCompletion();

		// スレッド起動
		service = Executors.newSingleThreadScheduledExecutor();
		future = service.schedule(this, 0, TimeUnit.SECONDS);
		Benchmarker.logger.log(Level.INFO, "{0} start subscribing", new Object[]{clientId});
	}

	public void stop() throws MqttException, InterruptedException {
		// スレッド停止
		if (!future.isDone()) {
			isTerminate = true;
			future.cancel(false);
			queue.offer(new Record(true));
		}
		if (service != null) {
			service.shutdown();
			service.awaitTermination(10, TimeUnit.SECONDS);
		}

		// 切断
		if (client != null) {
			IMqttToken mt = client.unsubscribe(opts.topic);
			mt.waitForCompletion();
			client.disconnect();
		}
		Benchmarker.logger.log(Level.INFO, "{0} stop subscribing", new Object[]{clientId});
	}

	public void run() {
		try {
			client.subscribe(opts.topic, opts.qos);

			bw.write(Record.toCsvHeader());
			bw.newLine();
			while (true) {
				Record record = queue.take();
				if (record.isLast()) {
					break;
				}
				bw.write(record.toCsvRow());
				bw.newLine();
			}
		} catch (MqttException | InterruptedException | IOException e) {
			Benchmarker.logger.log(Level.SEVERE, clientId + " failed to subscribe.", e);
			throw new RuntimeException(e);
		}
	}


	@Override
	public void disconnected(MqttDisconnectResponse disconnectResponse) {
	}

	@Override
	public void mqttErrorOccurred(MqttException exception) {
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		if (isTerminate) return;
		long receiveTime = Instant.now().toEpochMilli();
		Payload payload;
		try {
			payload = mapper.readValue(message.getPayload(), Payload.class);
			Record record = new Record(
					payload.clientId,
					clientId,
					payload.messageId,
					payload.sendTime,
					receiveTime
			);
			queue.offer(record);
			if (pingSender != null) {
				// キャストではなくmessageArrivedの処理自体をExtendedPingSenderのサブクラスによって変更する方が良い？
				StatusPingSender statusPingSender = (StatusPingSender) pingSender;
				statusPingSender.updateProcessingTimePerMsg(Instant.now().toEpochMilli() - receiveTime);
			}
		} catch (Exception e) {
			Benchmarker.logger.log(Level.SEVERE, "{0} received invalid payload {1}", new Object[]{clientId, message.getPayload()});
			return;
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
