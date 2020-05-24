package pt.ulisboa.tecnico.cnv.worker.heartbeat;

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

public class Heartbeat implements Runnable {

	private final String loadbalancerIP;
	private final String workerId;
	private final AmazonEC2 ec2;

	public Heartbeat() {
		System.out.println("Heartbeat thread initialized");
		ec2 = AmazonEC2ClientBuilder.defaultClient();
		this.loadbalancerIP = getLoadbalancerIP();
		this.workerId = getWorkerId();
	}

	public void run() {
	    heartbeat();
	}

	/**
	 * Sends a heartbeat to the respective endpoint on the loadbalancer
	 **/
	private void heartbeat() {

		HttpURLConnection connection = null;

		try {
			String urlParameters = "?workerId=" + workerId;
			URL url = new URL("http://" + loadbalancerIP + "/heartbeat" + urlParameters);

			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(false);
			connection.setRequestProperty("Content-Length",
	Integer.toString(urlParameters.getBytes().length));

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
			System.out.println("Failed to send heartbeat");
			System.out.println(e.getMessage());

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

	}

	/**
	 * Obtains the workerID from the ec2 instance aws metadata endpoint
	 **/
	private String getWorkerId() {
		String metadataURL = "http://169.254.169.254/latest/meta-data/instance-id";
		StringBuilder result = new StringBuilder();
		HttpURLConnection conn;

		try {
			URL url = new URL(metadataURL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			rd.close();
		} catch (Exception e) {
			System.out.println("Failed to get instanceID from metadata endpoint");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		return result.toString();
	}

	/**
	 * Obtains the Loadbalancer's public IP address from a tag on the
	 * worker machine.
	 **/
	private String getLoadbalancerIP() {
		System.out.println("Initial loadbalancer instance lookup");

		Filter tagFilter = new Filter("tag:type")
			.withValues("loadbalancer");
		Filter statusFilter = new Filter("instance-state-name")
			.withValues("running");

		DescribeInstancesRequest request = new DescribeInstancesRequest()
			.withFilters(tagFilter, statusFilter);

		// Find the running loadbalancer
		DescribeInstancesResult response = ec2.describeInstances(request);

		String loadbalancerIP = "";
		for (Reservation reservation : response.getReservations()) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getState().getName().equals("running")) {
					loadbalancerIP = instance.getPublicIpAddress();
					System.out.println("Found loadbalancer's IP");
				}
			}
		}

		if (loadbalancerIP == "") {
			System.out.println("no loadbalancer found. Check if you have loadbalancer with tag 'type:loadbalancer'");
		}

		return loadbalancerIP;
	}
}
