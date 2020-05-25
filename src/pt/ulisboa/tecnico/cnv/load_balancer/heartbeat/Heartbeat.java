package pt.ulisboa.tecnico.cnv.load_balancer.heartbeat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.DataOutputStream;
import java.io.BufferedWriter;
import java.util.List;

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

public class Heartbeat implements Runnable {

	private static final String LOG_TAG = Heartbeat.class.getSimpleName();

	// FIXME this is probably not right
	private final AmazonEC2 ec2;
	private HeartbeatInterface heartbeatInterface;

	public Heartbeat(HeartbeatInterface heartbeatInterface) {
		System.out.println("Heartbeat thread initialized");
		ec2 = AmazonEC2ClientBuilder.defaultClient();
		this.heartbeatInterface = heartbeatInterface;
	}

	public void run() {
		for (String workerIP : heartbeatInterface.getWorkerIPs())
			heartbeat(workerIP);
	}

	/**
	 * Sends a heartbeat to the respective endpoint on the worker
	 **/
	private void heartbeat(String workerIP) {

		HttpURLConnection connection = null;

		try {
			Log.i(LOG_TAG, "sending ping to worker: " + workerIP);
			URL url = new URL("http://" + workerIP + ":8000/heartbeat");

			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(false);
			connection.setRequestProperty("Content-Length",
						      Integer.toString(0));

			//Get Response
			InputStream is = connection.getInputStream();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = rd.readLine()) != null) {
				response.append(line);
				response.append('\r');
			}
			rd.close();

			System.out.println("Heartbeat " + response.toString());

		} catch (Exception e) {
			System.out.println("Failed to send ping");
			System.out.println(e.getMessage());

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

	}
}
