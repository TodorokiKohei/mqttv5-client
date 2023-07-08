package org.eclipse.paho.sample.mqttv5app;

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

    public static void main(String[] args) {
        new ShareSample(
                12000, 1, 3, "tcp://localhost:1883", 2, 1, "t", 100
        ).run();
    }

    public ShareSample(int execTime, int pubNum, int subNum, String serverURI, int keepAlive, int qos, String topic, int publishInterval) {
        this.execTime = execTime;
        this.pubNum = pubNum;
        this.subNum = subNum;

        this.config = new CommonConfig();
        this.config.serverURI = serverURI;
        this.config.keepAlive = keepAlive;
        this.config.qos = qos;
        this.config.topic = topic;
        this.config.publishInterval = publishInterval;
    }

    public void run() {
        CountDownLatch latch = new CountDownLatch(pubNum + subNum);

        ArrayList<SubBG> subList = new ArrayList<>();
        for (int i = 0; i < subNum; i++) {
            try {
                SubBG sub = new SubBG(config, "sub-" + Integer.valueOf(i).toString(), latch);
                subList.add(sub);
                sub.connect();
                sub.setPingReqPayload("{\"canSend\":true}");
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
            subList.get(0).setPingReqPayload("{\"canSend\":false}");
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

    class CommonConfig {
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

        public clientBG(CommonConfig config, String clientId) {
            this.config = config;
            this.clientId = clientId;
        }

        public void connect() throws MqttException {
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttAsyncClient(config.serverURI, clientId, persistence);
            setCallback();

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

        abstract public void setCallback();
    }


    class PubBG extends clientBG implements Runnable {

        private CountDownLatch latch;

        public PubBG(CommonConfig config, String clientId, CountDownLatch latch) {
            super(config, clientId);
            this.latch = latch;
        }

        @Override
        public void setCallback() {
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

        private CountDownLatch latch;
        private List<String> results;
        private long startTime;

        public SubBG(CommonConfig config, String clientId, CountDownLatch latch) {
            super(config, clientId);
            this.latch = latch;
            this.results = new ArrayList<String>();
        }

        @Override
        public void setCallback() {
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

        public void setPingReqPayload(String payload) {

        }
    }
}
