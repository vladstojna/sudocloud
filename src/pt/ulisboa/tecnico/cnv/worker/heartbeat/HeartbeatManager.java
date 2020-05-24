package pt.ulisboa.tecnico.cnv.worker.heartbeat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import pt.ulisboa.tecnico.cnv.worker.heartbeat.Heartbeat;

public class HeartbeatManager {
	private static final long PERIOD = 5000L; // heartbeat every 5 seconds
	private static final long DELAY = 0L;

	private ScheduledExecutorService executor;
	private Heartbeat heartbeat;

	public HeartbeatManager() {
		heartbeat = new Heartbeat();
		executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate((Runnable) heartbeat, DELAY, PERIOD, TimeUnit.MILLISECONDS);
	}
}
