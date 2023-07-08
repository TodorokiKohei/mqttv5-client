package org.eclipse.paho.sample.mqttv5app;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Publisher {
    public static void main(String[] args) {

        String url = "tcp://localhost:1883";
        String clientId = "publisher";
        MemoryPersistence persistence = new MemoryPersistence();

        MqttConnectionOptions conOpts = new MqttConnectionOptions();
        conOpts.setKeepAliveInterval(2);


        MqttAsyncClient client = null;
        try {
            client = new MqttAsyncClient(url, clientId, persistence);
            IMqttToken mt =  client.connect(conOpts);
            mt.waitForCompletion();
            System.out.println("Connected");

            Thread.sleep(1000);
            for (int i = 0; i < 10; i++) {
                MqttMessage ms = new MqttMessage("Hello World".getBytes());
                ms.setQos(1);
                client.publish("t", ms);
                Thread.sleep(1000);
            }

            System.out.println("Wait: please enter");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            br.readLine();

            System.out.println("Disconnected");
            client.disconnect();
        } catch (MqttException e) {
            System.out.println("reason "+e.getReasonCode());
            System.out.println("msg "+e.getMessage());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
