package pt.ulisboa.tecnico.cnv.load_balancer.fault_tolerance;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.DataOutputStream;
import java.io.BufferedWriter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

import java.net.SocketTimeoutException;

import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.InstanceManager;
import pt.ulisboa.tecnico.cnv.load_balancer.AutoScaler;

public class WorkerPing implements Runnable {

	private static final String LOG_TAG = WorkerPing.class.getSimpleName();

	private static final int PING_TIMEOUT =  3; // ping timeout in seconds
	private final AmazonEC2 ec2;

	private final InstanceManager instanceManager;
	private WorkerPingListener workerPingListener;
	private AutoScaler autoScaler;

    public WorkerPing(InstanceManager im, WorkerPingListener workerPingListener, AutoScaler autoScaler) {
		ec2 = AmazonEC2ClientBuilder.defaultClient();
		this.instanceManager = im;
		this.workerPingListener = workerPingListener;
		this.autoScaler = autoScaler;
	}

	public void run() {
		for (final WorkerInstanceHolder holder : instanceManager.getInstances()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();

			Future<?> result = executor.submit(new Runnable() {
					@Override
					public void run() {
						Log.i(LOG_TAG, "Started running ping");
						ping(holder);
						Log.i(LOG_TAG, "Finished ping");
					}
				});

			long before = System.currentTimeMillis();

			try {
				result.get(PING_TIMEOUT, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				Log.i(LOG_TAG, "Aborted ping due to timeout");
				workerPingListener.onInstanceUnreachable(holder, autoScaler);
				result.cancel(true);
			} catch (InterruptedException e ) {
				Log.i(LOG_TAG, "Ping interrupted due to timeout");
				e.printStackTrace();
			} catch (ExecutionException e ) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sends a ping to the respective endpoint on the worker
	 * @return success state of the ping
	 **/
	private boolean ping(WorkerInstanceHolder worker) {

		HttpURLConnection connection = null;
		StringBuilder response = new StringBuilder();

		try {
			Log.i(LOG_TAG, "sending ping to worker: " + worker.getInstanceId());
			URL url = new URL("http://" + worker.getInstance().getPrivateIpAddress() + ":8000/status");

			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(false);
			connection.setRequestProperty("Content-Length",
						      Integer.toString(0));

			Log.i(LOG_TAG, "2 sending ping to worker: " + worker.getInstanceId());

			//Get Response
			InputStream is = connection.getInputStream();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = rd.readLine()) != null) {
			    Log.i(LOG_TAG, "3 sending ping to worker: " + worker.getInstanceId());

				response.append(line);
				response.append('\r');
			}
			rd.close();

			Log.i(LOG_TAG, "Worker " + worker.getInstanceId() + " status: " + response.toString());
		} catch (SocketTimeoutException e) {
			Log.i(LOG_TAG, "Worker " + worker.getInstanceId() + " timed out");
			return false;
		} catch (Exception e) {
			Log.i(LOG_TAG, "Worker " + worker.getInstanceId() + " is unreachable");
			return false;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		return true;

	}
}
