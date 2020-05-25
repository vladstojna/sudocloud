package pt.ulisboa.tecnico.cnv.load_balancer.fault_tolerance;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import pt.ulisboa.tecnico.cnv.load_balancer.InstanceManager;

public class WorkerPingScheduler {
	private static final long PERIOD = 5000L; // ping every 5 seconds
	private static final long DELAY = 0L;

	private ScheduledExecutorService executor;
	private WorkerPing workerPing;

	public WorkerPingScheduler(InstanceManager instanceManager,  WorkerPingListener workerPingListener) {
		workerPing = new WorkerPing(instanceManager, workerPingListener);
		executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate((Runnable) workerPing, DELAY, PERIOD, TimeUnit.MILLISECONDS);
	}
}
