package org.eclipse.paho.sample.mqttv5app;

import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.sample.mqttv5app.pingsender.StatusPingSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Subscriber implements MqttCallback {
    static private StatusPingSender pingSender;

    public static void main(String[] args) {

        String url = "tcp://localhost:1883";
        String clientId = "subscriber";
        MemoryPersistence persistence = new MemoryPersistence();

        MqttConnectionOptions conOpts = new MqttConnectionOptions();
        conOpts.setKeepAliveInterval(2);

//        ExtendedPingSender pingSender = new SamplePingSender("Hello World!!");
        pingSender = new StatusPingSender(100);
        pingSender.setPingIntervalMilliSeconds(1000);

        MqttAsyncClient client = null;
        try {
            client = new MqttAsyncClient(url, clientId, persistence, pingSender, null);
            client.resizeReceiverQueueSize(100);

            Subscriber sub = new Subscriber();
            client.setCallback(sub);

            IMqttToken mt =  client.connect(conOpts);
            mt.waitForCompletion();
            System.out.println("Connected");

            client.subscribe("t", 1);
            System.out.println("Subscribe");

            System.out.println("Wait: please enter");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            br.readLine();

            System.out.println("Disconnected");
            client.disconnect();
        } catch (MqttException e) {
            System.out.println("reason "+e.getReasonCode());
            System.out.println("msg "+e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        System.out.println("Disconnected");
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        System.out.println("Error Occurred");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        long startTime = System.currentTimeMillis();
        Thread.sleep(800);
        long processingTime = System.currentTimeMillis() - startTime;
        pingSender.updateProcessingTimePerMsg(processingTime / 1000.0);

    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        System.out.println("Delivery Complete");
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        System.out.println("Connection Complete");
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        System.out.println("Auth Packet Arrived");
    }
}
