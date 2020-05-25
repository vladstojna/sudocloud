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

import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.InstanceManager;

public class WorkerPing implements Runnable {

	private static final String LOG_TAG = WorkerPing.class.getSimpleName();

	// FIXME this is probably not right
	private final AmazonEC2 ec2;
	private final InstanceManager instanceManager;

	public WorkerPing(InstanceManager im) {
		ec2 = AmazonEC2ClientBuilder.defaultClient();
		this.instanceManager = im;
	}

	public void run() {
		for (WorkerInstanceHolder holder : instanceManager.getInstances())
			heartbeat(holder);
	}

	/**
	 * Sends a heartbeat to the respective endpoint on the worker
	 **/
	private void heartbeat(WorkerInstanceHolder worker) {

		HttpURLConnection connection = null;
		StringBuilder response = new StringBuilder();

		try {
			Log.i(LOG_TAG, "sending ping to worker: " + worker.getInstanceId());
			URL url = new URL("http://" + worker.getPublicIpAddress() + ":8000/status");

			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(false);
			connection.setRequestProperty("Content-Length",
						      Integer.toString(0));

			//Get Response
			InputStream is = connection.getInputStream();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = rd.readLine()) != null) {
				response.append(line);
				response.append('\r');
			}
			rd.close();

			Log.i(LOG_TAG, "Worker " + worker.getInstanceId() + " status: " + response.toString());

		} catch (Exception e) {
			Log.i(LOG_TAG, "Worker " + worker.getInstanceId() + " is unreachable");

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

	}
}
