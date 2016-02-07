package com.roeper.bu.urop.recorder;

import java.io.File;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.roeper.bu.urop.lib.BrokerConfig;
import com.roeper.bu.urop.lib.ConfigReader;
import com.roeper.bu.urop.lib.SensorReading;
import com.roeper.bu.urop.lib.SensorReadingWriter;

public class Recorder implements MqttCallback {

	public static void main(String args[]) throws Exception {
		
		String configFile = "config.yml";
		if (args.length == 1)
		{
			configFile = args[0];
		}
		
		// get the config
		RecorderConfig config = null;
		ConfigReader<RecorderConfig> reader = new ConfigReader<RecorderConfig>(RecorderConfig.class);
		try {
			config = reader.read(configFile);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error getting config");
		}

		// create recorder
		final Recorder recorder = new Recorder(config);

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				recorder.stop();
			}
		});

		// start recorder
		recorder.start();
	}

	final Logger logger = LoggerFactory.getLogger(Recorder.class);
	
	private RecorderConfig config;
	private SensorReadingWriter writer;
	private MqttClient client;

	public Recorder(RecorderConfig aConfig) {
		this.config = aConfig;
	}

	public void start() {
		// create the reading writer
		String filename = "take-" + System.currentTimeMillis() + ".txt";
		String destiantionFile = this.config.getDestinationFolder() + "/" + filename;
		File destinationFile = new File(destiantionFile);
		//create all necessary parent folders
		if (destinationFile.getParentFile() != null)
		{
			destinationFile.getParentFile().mkdirs();
		}
		writer = new SensorReadingWriter(destinationFile);
		logger.info("Writing data to: {}", destinationFile.getAbsolutePath());

		MemoryPersistence persistence = new MemoryPersistence();
		try {
			BrokerConfig brokerConfig = this.config.getBrokerConfig();
			String clientId = "data-recorder-" + (int)(Math.random() * 99999);
			client = new MqttClient(brokerConfig.getHostname(), clientId, persistence);
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			client.connect(connOpts);
			logger.info("Successfully connected to broker.");
			client.setCallback(this);
			
			//subscribe to receive sensor readings
			String subscribeDest = this.config.getBrokerConfig().getTopicPrefix() + "/#";
			logger.info("Subscribing to: {}", subscribeDest);
			client.subscribe(subscribeDest);
		} catch (MqttException me) {
			logger.error("An error occured connecting to the broker");
			me.printStackTrace();
		}
	}

	public void stop() {
		logger.info("Stopping...");
		writer.flush();
		try {
			client.disconnect();
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	public void connectionLost(Throwable arg0) {
		logger.info("Lost connection. All data collected will be saved");
		writer.flush();
	}

	public void deliveryComplete(IMqttDeliveryToken arg0) {

	}

	public void messageArrived(String aTopic, MqttMessage message) throws Exception {
		String payload = new String(message.getPayload());
		//remove prefix
		String topic = aTopic.substring(this.config.getBrokerConfig().getTopicPrefix().length());
		String[] levels = topic.split("/");
		
		//basic sanity checking
		if (levels.length == 5 && levels[1].equals("group") && levels[3].equals("sensor")) {
			//get sensor info from topic structure:
			//e.g. <prefix>/group/<id>/sensor/<id>
			String groupId = levels[2];
			String sensorId = levels[4];
			
			//more checking
			String[] readings = (payload).split(",");
			if (readings.length != 6) {
				// log invalid sensor reading
				logger.warn("Recevied an invalid sensor reading. Topic: {} Payload: {}", aTopic, payload);
			} else {
				try {
					int red = Integer.parseInt(readings[0]);
					int green = Integer.parseInt(readings[1]);
					int blue = Integer.parseInt(readings[2]);
					int white = Integer.parseInt(readings[3]);
					int time1 = Integer.parseInt(readings[4]);
					int time2 = Integer.parseInt(readings[5]);
					SensorReading reading = new SensorReading(groupId, sensorId, red, green, blue, white, time1, time2);
					writer.write(reading);
				} catch (NumberFormatException e) {
					e.printStackTrace();
					// log invalid number
					logger.warn("An error occured processing a sensor reading. Topic: {} Payload: {}", aTopic, payload);
				}
			}
		}
		else
		{
			//log invalid topic
			logger.warn("Recevied an invalid topic. Topic: {} Payload: {}", aTopic, payload);
		}
	}
}
