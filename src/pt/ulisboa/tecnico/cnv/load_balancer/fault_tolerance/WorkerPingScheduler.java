package pt.ulisboa.tecnico.cnv.load_balancer.fault_tolerance;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorkerPingScheduler {
	private static final long PERIOD = 5000L; // heartbeat every 5 seconds
	private static final long DELAY = 0L;

	private ScheduledExecutorService executor;
	private WorkerPing workerPing;

	public WorkerPingScheduler(WorkerPingInterface workerPingInterface) {
		workerPing = new WorkerPing(workerPingInterface);
		executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate((Runnable) workerPing, DELAY, PERIOD, TimeUnit.MILLISECONDS);
	}
}
