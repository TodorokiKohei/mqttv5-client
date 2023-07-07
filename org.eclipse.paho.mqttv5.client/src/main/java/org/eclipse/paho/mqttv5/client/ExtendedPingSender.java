package org.eclipse.paho.mqttv5.client;

import org.eclipse.paho.mqttv5.client.internal.ClientComms;
import org.eclipse.paho.mqttv5.client.logging.Logger;
import org.eclipse.paho.mqttv5.client.logging.LoggerFactory;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttPingReq;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

public abstract class ExtendedPingSender implements MqttPingSender {
	private static final String CLASS_NAME = ExtendedPingSender.class.getName();
	protected Logger log = LoggerFactory.getLogger(LoggerFactory.MQTT_CLIENT_MSG_CAT, CLASS_NAME);

	protected ClientComms comms;

	private Timer timer;
	private ScheduledExecutorService executorService = null;
	private ScheduledFuture<?> scheduledFuture;
	private String clientid;

	private long pingIntervalMilliSeconds = -1;

	@Override
	public void init(ClientComms comms) {
		final String methodName = "init";

		if (comms == null) {
			throw new IllegalArgumentException("ClientComms cannot be null.");
		}
		this.comms = comms;

		clientid = comms.getClient().getClientId();
		log.setResourceName(clientid);
	}

	@Override
	public void start() {
		final String methodName = "start";

		//@Trace 659=start timer for client:{0}
		log.fine(CLASS_NAME, methodName, "659", new Object[]{clientid});

		// Extended PINGREQ must be enabled.
		if (!comms.getConOptions().isEnableExPingReq()) {
			throw new IllegalStateException("Extended PINGREQ is disabled.");
		}
		log.info(CLASS_NAME, methodName, "Extended PINGREQ is enabled");

		long delay = getDelay(comms.getKeepAlive());
		if (executorService == null) {
			timer = new Timer("MQTT Ping: " + clientid);
			timer.schedule(new PingTask(), delay);
		} else {
			schedule(delay);
		}
	}

	@Override
	public void stop() {
		final String methodName = "stop";

		//@Trace 661=stop
		log.info(CLASS_NAME, methodName, "661", null);
		if (executorService == null) {
			if (timer != null) {
				timer.cancel();
			}
		} else {
			if (scheduledFuture != null) {
				scheduledFuture.cancel(true);
			}
		}
	}

	@Override
	public void schedule(long delayInMilliseconds) {
		long delay = getDelay(delayInMilliseconds);
		if (executorService == null) {
			timer.schedule(new PingTask(), delay);
		} else {
			scheduledFuture = executorService.schedule(new PingRunnable(), delay, TimeUnit.MILLISECONDS);
		}
	}

	private class PingTask extends TimerTask {
		private static final String methodName = "PingTask.run";

		@Override
		public void run() {
			Thread.currentThread().setName("MQTT Ping: " + clientid);
			//@Trace 660=Check schedule at {0}
			log.info(CLASS_NAME, methodName, "660", new Object[]{Long.valueOf(System.nanoTime())});

			// Create PINGREQ before checkForActibity
			try {
				MqttPingReq pingReq = createPingreq();
				MqttToken token = new MqttToken(clientid);
				comms.sendNoWait(pingReq, token);    // update pingCommand of ClientState
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
			comms.checkForActivity();
		}
	}

	private class PingRunnable implements Runnable {
		private static final String methodName = "PingTask.run";

		public void run() {
			String originalThreadName = Thread.currentThread().getName();
			Thread.currentThread().setName("MQTT Ping: " + clientid);
			//@Trace 660=Check schedule at {0}
			log.info(CLASS_NAME, methodName, "660", new Object[]{Long.valueOf(System.nanoTime())});

			// Create PINGREQ before checkForActibity
			try {
				MqttPingReq pingReq = createPingreq();
				MqttToken token = new MqttToken(clientid);
				comms.sendNoWait(pingReq, token);    // update pingCommand of ClientState
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
			comms.checkForActivity();
			Thread.currentThread().setName(originalThreadName);
		}
	}


	public void setPingIntervalMilliSeconds(int pingIntervalMilliSeconds) {
		this.pingIntervalMilliSeconds = pingIntervalMilliSeconds;
	}

	private long getDelay(long delayInMilliseconds) {
		if (pingIntervalMilliSeconds == -1) return delayInMilliseconds;
		return min(pingIntervalMilliSeconds, delayInMilliseconds);
	}

	abstract protected MqttPingReq createPingreq();
}
