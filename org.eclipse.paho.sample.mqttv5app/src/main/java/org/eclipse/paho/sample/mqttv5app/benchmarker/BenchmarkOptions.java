package org.eclipse.paho.sample.mqttv5app.benchmarker;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BenchmarkOptions {
	public String logLevel;

	// 共通オプション
	public String mode;
	public int numClients;
	public int executionTime;
	public String brokerUrl;
	public String topic;
	public int qos;

	// パブリッシャー用オプション
	public int messageSize;
	public int throughput;
	public String unit;

	// サブスクライバー用オプション
	public boolean enableExtendedPingSender;
	public String outputPath;

	public void setEnvVar(){
		logLevel = EnvVarFetcher.getEnvVarOrDefault("LOG_LEVEL", "INFO");

        mode = EnvVarFetcher.getEnvVarOrDefault("MODE", null);
        numClients = EnvVarFetcher.getEnvVarOrDefault("NUM_CLIENTS", 1);
        executionTime = EnvVarFetcher.getEnvVarOrDefault("EXECUTION_TIME", 30);
        brokerUrl = EnvVarFetcher.getEnvVarOrDefault("BROKER_URL", "tcp://localhost:1883");
        topic = EnvVarFetcher.getEnvVarOrDefault("TOPIC", "test");
        qos = EnvVarFetcher.getEnvVarOrDefault("QOS", 0);

        messageSize = EnvVarFetcher.getEnvVarOrDefault("MESSAGE_SIZE", 100);
        throughput = EnvVarFetcher.getEnvVarOrDefault("THROUGHPUT", 100);
        unit = EnvVarFetcher.getEnvVarOrDefault("UNIT", "msg/s");

		enableExtendedPingSender = EnvVarFetcher.getEnvVarOrDefault("ENABLE_EXTENDED_PING_SENDER", false);
		outputPath = EnvVarFetcher.getEnvVarOrDefault("OUTPUT_PATH", "results");
    }

	public long getPublishInterval(TimeUnit timeUnit) {
		if (throughput == 0) {
			return 0;
		}

		long second = 1;
		if (timeUnit == TimeUnit.MILLISECONDS) {
			second = 1000;
        } else if (timeUnit == TimeUnit.MICROSECONDS) {
            second = 1000000;
        } else if (timeUnit == TimeUnit.NANOSECONDS) {
            second = 1000000000;
        }

		if (unit.contains("msg/s")) {
			return second / throughput;
		} else {
			return 0;
		}
	}

	public void printOptions(){
		Benchmarker.logger.log(Level.INFO, "logLevel: {0}, mode: {1}, numClients: {2}, executionTime: {3}, brokerUrl: {4}, topic: {5}, qos: {6}, messageSize: {7}, throughput: {8}, unit: {9}, enableExtendedPingSender: {10}",
                new Object[]{logLevel, mode, numClients, executionTime, brokerUrl, topic, qos, messageSize, throughput, unit, enableExtendedPingSender});
	}
}
