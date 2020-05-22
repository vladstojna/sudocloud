package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
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
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
import com.amazonaws.client.builder.AwsClientBuilder;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;

import java.util.Arrays;
import java.util.Date;

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
    static final String WORKER_AMI_ID = "ami-004f6abe9e4e405ad";
    static final String SECURITY_GROUP = "CNV-ssh+http";
    static final String EC2_REGION = "us-east-1";
    
    // in seconds
    static final int CW_PERIOD = 30;
    static final int CW_OFFSET = 60;

    private AmazonEC2 ec2;

    private LoadBalancer lb;
    
    private AmazonCloudWatch cw;
	
	public ScalerThread(LoadBalancer lb) {
	this.lb = lb;
	ec2 = AmazonEC2ClientBuilder
	    .standard()
	    .withRegion(EC2_REGION)
	    .build();
	this.cw = AmazonCloudWatchClientBuilder
			.standard()
			.withRegion(EC2_REGION)
			.build();
	this.getCloudWatchStatistics();
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

	Tag worker_tag = new Tag()
	    .withKey("type")
	    .withValue("worker");

	TagSpecification worker_tag_spec = new TagSpecification()
	    .withTags(worker_tag)
	    .withResourceType(ResourceType.Instance);

	RunInstancesRequest run_request = new RunInstancesRequest()
	    .withImageId(WORKER_AMI_ID)
	    .withInstanceType(InstanceType.T1Micro)
	    .withMaxCount(1)
	    .withMinCount(1)
	    .withSecurityGroups(SECURITY_GROUP)
	    .withMonitoring(true)
	    .withTagSpecifications(worker_tag_spec);

	RunInstancesResult run_response = ec2.runInstances(run_request);

	Instance instance = run_response.getReservation().getInstances().get(0);
	String reservation_id = instance.getInstanceId();

	this.lb.addInstance(new WorkerInstanceHolder(instance));

	Log.i(LOG_TAG, String.format("Successfully started EC2 instance %s based on AMI %s", reservation_id, WORKER_AMI_ID));
    }
    
    public void getCloudWatchStatistics() {
    	long currentTime = new Date().getTime();
	    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
			    .withStartTime(new Date(currentTime - (CW_OFFSET * 1000)))
			    .withNamespace("AWS/EC2")
			    .withPeriod(CW_PERIOD)
			    .withMetricName("CPUUtilization")
			    .withStatistics("Average")
			    .withEndTime(new Date(currentTime));
	    GetMetricStatisticsResult getMetricStatisticsResult = this.cw.getMetricStatistics(request);
	
	    for (Datapoint dp : getMetricStatisticsResult.getDatapoints()) {
		    System.out.println(dp.getTimestamp() + " : " + dp.getAverage());
	    }
    }
}
