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

    static final String LOG_TAG = LoadBalancer.class.getSimpleName();

    public final static AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

    public static List<Request> runningRequests = new ArrayList<>();
    private static List<WorkerInstanceHolder> workerInstances = new ArrayList<>();

    private LoadBalancer() {}

    public static void init() {
	initRunningWorkerInstances();
	Log.i(LOG_TAG, "Loadbalancer initialized");
    }

    /**
     * Obtains the instance that should be used for the next operation
     **/
    public static WorkerInstanceHolder getWorkerInstance() {
	return workerInstances.get(0);
    }

    /**
     * Informs the loadbalancer that a request has started processing
     **/
    public static void startedProcessing(Request request) {
	Log.i(LOG_TAG, String.format("Added request %d to list of processing requests", request.getId()));
	runningRequests.add(request);
	Log.i("Running requests: " + runningRequests.size());
    }

    /**
     * Informs the loadbalancer that the request has finished processing
     **/
    public static void finishedProcessing(Request request) {
	runningRequests.remove(request);
	Log.i("Running requests: " + runningRequests.size());
    }

    /**
     * Find the worker instances and adds them to 
     *
     * They are tagged with the tag "type:worker"
     **/
    private static void initRunningWorkerInstances() {
	Log.i(LOG_TAG, "Initial worker instance lookup");
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
		    workerInstances.add(new WorkerInstanceHolder(instance));
		    Log.i(LOG_TAG, "Found worker instance with id " +
				       instance.getInstanceId());
		}
	    }

	} catch (SdkClientException e) {
	    e.getStackTrace();
	}

	if (workerInstances.size() == 0) {
	    Log.i(LOG_TAG, "No running worker instance was found");
	    Log.i(LOG_TAG, "Maybe you forgot to tag them with 'type:worker'");
	}
    }
}
