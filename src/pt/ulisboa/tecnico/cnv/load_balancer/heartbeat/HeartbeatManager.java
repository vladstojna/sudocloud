package pt.ulisboa.tecnico.cnv.load_balancer.heartbeat;

import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatManager { 

    private static final long PERIOD = 5000L; // heartbeat every 5 seconds
    private static final long DELAY = 0L;
 
    private HeartbeatListener listener;
    private ScheduledExecutorService executor;
    private HeartbeatCheck heartbeatCheck;

    public HeartbeatManager(HeartbeatListener listener) {
	heartbeatCheck = new HeartbeatCheck(listener);
	executor = Executors.newSingleThreadScheduledExecutor();
	executor.scheduleAtFixedRate((Runnable) heartbeatCheck, DELAY, PERIOD, TimeUnit.MILLISECONDS);
    }
}
