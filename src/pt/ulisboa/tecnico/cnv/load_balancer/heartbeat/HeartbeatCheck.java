package pt.ulisboa.tecnico.cnv.load_balancer.heartbeat;

import pt.ulisboa.tecnico.cnv.load_balancer.heartbeat.HeartbeatListener;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

import java.util.List;
import java.util.ArrayList;

/**
 * Checks which heartbeat are missing
 **/
public class HeartbeatCheck implements Runnable {

	private static final String LOG_TAG = HeartbeatCheck.class.getSimpleName();

	private HeartbeatListener listener;
	private List<String> heartbeatsReceived = new ArrayList<>();

	public HeartbeatCheck(HeartbeatListener listener) {
	}

	public void run() {
		heartbeatsReceived.clear();
		checkHeartbeats();
	}

	public void checkHeartbeats() {
		Log.i(LOG_TAG, "Checked heartbeats");
	}

	
}
