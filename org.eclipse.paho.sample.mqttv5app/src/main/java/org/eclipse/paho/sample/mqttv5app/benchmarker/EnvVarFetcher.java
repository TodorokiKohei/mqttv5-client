package org.eclipse.paho.sample.mqttv5app.benchmarker;

public class EnvVarFetcher {
	public static String getEnvVarOrDefault(String envVarName, String defaultValue) {
		String envVarValue = System.getenv(envVarName);
		return envVarValue != null ? envVarValue : defaultValue;
	}

	public static int getEnvVarOrDefault(String envVarName, int defaultValue) {
		String envVarValue = System.getenv(envVarName);
		return envVarValue != null ? Integer.parseInt(envVarValue) : defaultValue;
	}

	public static boolean getEnvVarOrDefault(String envVarName, boolean defaultValue) {
		String envVarValue = System.getenv(envVarName);
		return envVarValue != null ? Boolean.parseBoolean(envVarValue) : defaultValue;
	}

	public static long getEnvVarOrDefault(String envVarName, long defaultValue) {
		String envVarValue = System.getenv(envVarName);
		return envVarValue != null ? Long.parseLong(envVarValue) : defaultValue;
	}

	public static double getEnvVarOrDefault(String envVarName, double defaultValue) {
		String envVarValue = System.getenv(envVarName);
		return envVarValue != null ? Double.parseDouble(envVarValue) : defaultValue;
	}
}
