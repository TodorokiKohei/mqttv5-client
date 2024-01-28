package org.eclipse.paho.sample.mqttv5app.benchmarker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Benchmarker {

	public static final Logger logger = Logger.getLogger(Benchmarker.class.getName());

	private String randomId;
	private BenchmarkOptions opts;
	private List<Client> clients = new ArrayList<>();
	private List<BufferedWriter> writers = new ArrayList<>();

	public static void main(String[] args) {
		// Parse options
		BenchmarkOptions opts = new BenchmarkOptions();
		opts.setEnvVar();

		// Generate a unique ID for benchmark
		new Benchmarker(RandomStringUtils.randomAscii(16), opts).run();
	}

	public Benchmarker(String randomId, BenchmarkOptions opts) {
        this.randomId = randomId;
        this.opts = opts;

		// Set up logging
		logger.setLevel(Level.parse(opts.logLevel));
	}

	public void run(){
		opts.printOptions();
		if (opts.mode == null) {
			logger.log(Level.SEVERE, "MODE must be set to PUB or SUB");
			return;
		}
		else if (opts.mode.equals("PUB")) {
			for (int i = 0; i < opts.numClients; i++) {
				clients.add(new Publisher(opts, randomId + "-pub-"+ i));
			}
		}else if (opts.mode.equals("SUB")) {
			Path outputPath = Paths.get(opts.outputPath);
			if (Files.notExists(outputPath)) {
				try {
					Files.createDirectories(outputPath);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			for (int i = 0; i < opts.numClients; i++) {
				try {
					BufferedWriter bw = Files.newBufferedWriter(outputPath.resolve("sub-"+i+".csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					writers.add(bw);
					clients.add(new Subscriber(opts, randomId+"-sub-"+i, bw));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		} else {
			logger.log(Level.SEVERE, "Invalid mode {0}. MODE must be set to PUB or SUB", opts.mode);
			return;
		}

		// Start clients
		for (Client client : clients) {
            try {
                client.start();
            } catch (MqttException e) {
                logger.log(Level.SEVERE, "Failed to start client", e);
            }
        }

		// Wait for execution time
		try {
			Thread.sleep(opts.executionTime * 1000L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		// Wait for clients to finish
		for (Client client : clients) {
			try {
				client.stop();
			} catch (InterruptedException | MqttException e) {
				logger.log(Level.SEVERE, client.getClientId() + " failed to stop client {0}.", e);
			}
		}

		// Close writers
		for (BufferedWriter bw : writers) {
            try {
				bw.flush();
                bw.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to close writer", e);
            }
        }
	}
}
