package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.SdkClientException;

import java.util.List;
import java.util.ArrayList;

public class LoadBalancer {

    public final static AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

    public static List<Request> runningRequests = new ArrayList<>();

    private LoadBalancer() {
	System.out.println("Loadbalancer initialized");
    }

    /**
     * Obtains the instance that should be used for the next operation
     **/
    public static Instance getWorkerInstance(Request request) {
	runningRequests.add(request);
	System.out.println("Running requests: " + runningRequests.size());
	return findRunningWorkerInstances().get(0);
    }

    /**
     * Informs the loadbalancer that the request has finished processing
     **/
    public static void finishedProcessing(Request request) {
	runningRequests.remove(request);
	System.out.println("Running requests: " + runningRequests.size());
    }

    /**
     * Find the worker instances
     *
     * They are tagged with the tag "type:worker"
     **/
    private static List<Instance> findRunningWorkerInstances() {

	List<Instance> runningInstances = new ArrayList<Instance>();

	try {
	    //Create the Filter to use to find running instances
	    Filter filter = new Filter("tag:type");
	    filter.withValues("worker");

	    //Create a DescribeInstancesRequest
	    DescribeInstancesRequest request = new DescribeInstancesRequest();
	    request.withFilters(filter);

	    // Find the running instances
	    DescribeInstancesResult response = ec2.describeInstances(request);

	    for (Reservation reservation : response.getReservations()){
		for (Instance instance : reservation.getInstances()) {
		    runningInstances.add(instance);
		    System.out.println("Found worker instance with id " +
				       instance.getInstanceId());
		}
	    }

	} catch (SdkClientException e) {
	    e.getStackTrace();
	}

	if (runningInstances.size() == 0) {
	    System.out.println("No running worker instance was found");
	    System.out.println("Maybe you forgot to tag them with 'type:worker'");
	}
	return runningInstances;
    }
}
