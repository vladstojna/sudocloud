package pt.ulisboa.tecnico.cnv.worker;

import java.lang.Thread;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Filter;

public class HeartbeatThread extends Thread {

	private static final int heartbeatInterval = 5000; // heartbeat every 5 seconds
	private final String loadbalancerIP;
	private final String workerId;

	public HeartbeatThread() {
		System.out.println("Heartbeat thread initialized");
		this.loadbalancerIP = getLoadbalancerIP();
		this.workerId = getWorkerId();
	}

	public void run() {
		try {
			boolean running = true;
			while (running) {
				heartbeat();
				Thread.sleep(heartbeatInterval);
			}
		} catch(InterruptedException v) {
			System.out.println(v);
		} catch(Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private void heartbeat() {
		System.out.println("Heartbeat");
	}

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

	private String getLoadbalancerIP() {
		AmazonEC2 client = AmazonEC2ClientBuilder.standard().build();
		DescribeTagsRequest request = new DescribeTagsRequest()
			.withFilters(new Filter()
				     .withName("resource-id")
				     .withValues(getWorkerId()));
		DescribeTagsResult response = client.describeTags(request);
		List<TagDescription> tags = response.getTags();

		for (TagDescription tag : tags) {
			if (tag.getKey().equals("loadbalancer_ip"))
				return tag.getValue();
		}
		System.out.println("Error: loadbalancer_ip tag not present");
		return null;
	}
}
