package com.roeper.bu.urop.lib;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SensorReadingWriter {

	private ObjectMapper mapper = new ObjectMapper();
	private File destination;
	private List<SensorReading> buffer = new LinkedList<SensorReading>();
	private LinkedBlockingQueue<List<SensorReading>> writeJobs = new LinkedBlockingQueue<List<SensorReading>>();
	private int bufferSize = 1000;
	private AtomicBoolean done = new AtomicBoolean(false);

	public SensorReadingWriter(File aDestination) {
		this.destination = aDestination;
		(new Thread(new WriteReadingsWorker())).start();
	}

	public SensorReadingWriter(File aDestination, int aBufferSize) {
		this.destination = aDestination;
		this.bufferSize = aBufferSize;
		(new Thread(new WriteReadingsWorker())).start();
	}

	public void write(SensorReading aReading) {
		buffer.add(aReading);

		if (buffer.size() > bufferSize) {
			List<SensorReading> toWrite = this.buffer;
			this.buffer = new LinkedList<SensorReading>();
			writeJobs.add(toWrite);
		}
	}

	public void flush() {
		if (this.buffer != null) {
			List<SensorReading> toWrite = this.buffer;
			this.buffer = null;
			writeJobs.add(toWrite);
		}
		done.set(true);
	}

	private class WriteReadingsWorker implements Runnable {

		public void run() {

			while (!done.get() || !writeJobs.isEmpty()) {
				BufferedWriter bw = null;
				try {
					List<SensorReading> readings = writeJobs.take();
					// APPEND MODE SET HERE
					bw = new BufferedWriter(new FileWriter(destination, true));

					for (SensorReading reading : readings) {
						String toWrite = mapper.writeValueAsString(reading);
						bw.write(toWrite);
						bw.newLine();
					}

					bw.flush();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				} finally {
					if (bw != null)
						try {
							bw.close();
						} catch (IOException ioe2) {
						}
				}
			}
		}
	}
}
