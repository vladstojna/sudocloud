package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
/**
 * Autoscaler component. Runs in a background thread
 *
 *
 *
 *
 *
 **/
public class ScalerThread extends Thread {
    static final String LOG_TAG = ScalerThread.class.getSimpleName();
    static final String WORKER_AMI_ID = "ami-022e864c190a855fd";
    
    static AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

    public ScalerThread() {
    }

    public void run() {
	Log.i(LOG_TAG, "started autoscaler in background thread");
	//startWorkerInstance();
    }

    /**
     * Currently broken because of lack of permissions
     **/
    public void startWorkerInstance() {
	Log.i(LOG_TAG, "Starting new worker instance with name");
	
        RunInstancesRequest run_request = new RunInstancesRequest()
            .withImageId(WORKER_AMI_ID)
            .withInstanceType(InstanceType.T1Micro)
            .withMaxCount(1)
            .withMinCount(1)
	    .withSecurityGroups("project")
	    .withMonitoring(true);

	RunInstancesResult run_response = ec2.runInstances(run_request);

        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();

        Tag tag = new Tag()
            .withKey("type")
            .withValue("worker");

        CreateTagsRequest tag_request = new CreateTagsRequest()
            .withTags(tag);

        CreateTagsResult tag_response = ec2.createTags(tag_request);

        Log.i(LOG_TAG, String.format("Successfully started EC2 instance %s based on AMI %s", reservation_id, WORKER_AMI_ID));
    }
}
