package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Tag;

import java.util.*;

public class AutoScaler {
	
	public static final int MODE_AVERAGE = 1;
	public static final int MODE_MAXIMUM = 2;
	public static final int MODE_MINIMUM = 3;
	
	static final String LOG_TAG = AutoScaler.class.getSimpleName();
	static final String WORKER_AMI_ID = "ami-004f6abe9e4e405ad";
	static final String SECURITY_GROUP = "CNV-ssh+http";
	static final String EC2_REGION = "us-east-1";
	
	// in seconds and has to be multiple of 60
	static final int CW_PERIOD = 60;
	static final int CW_OFFSET = 10 * 60 * 60;
	
	// important to create and terminate instances
	AmazonEC2 ec2;
	// important for statistics
	AmazonCloudWatch cw;
	
	IEvaluationCallback evaluationCallback;
	Iterable<WorkerInstanceHolder> workers;
	// statistics mode
	int mode;
	double lowThreshold;
	double highThreshold;
	
	public AutoScaler(IEvaluationCallback evaluationCallback, Iterable<WorkerInstanceHolder> workers,
	                  int mode, double lowThreshold, double highThreshold) {
		this.ec2 = AmazonEC2ClientBuilder
				.standard()
				.withRegion(EC2_REGION)
				.build();
		this.cw = AmazonCloudWatchClientBuilder
				.standard()
				.withRegion(EC2_REGION)
				.build();
		this.evaluationCallback = evaluationCallback;
		this.workers = workers;
		this.mode = mode;
		this.lowThreshold = lowThreshold;
		this.highThreshold = highThreshold;
	}
	
	public WorkerInstanceHolder startInstance() {
		return this.startInstances(1).get(0);
	}
	
	public List<WorkerInstanceHolder> startInstances(int numberInstances) {
		Log.i(LOG_TAG, "Starting " + numberInstances + " new worker instance");
		Tag tag = new Tag()
				.withKey("type")
				.withValue("worker");
		TagSpecification tagSpecification = new TagSpecification()
				.withTags(tag)
				.withResourceType(ResourceType.Instance);
		RunInstancesRequest request = new RunInstancesRequest()
				.withImageId(WORKER_AMI_ID)
				.withInstanceType(InstanceType.T2Micro)
				.withMaxCount(numberInstances)
				.withMinCount(numberInstances)
				.withSecurityGroups(SECURITY_GROUP)
				.withMonitoring(true)
				.withTagSpecifications(tagSpecification);
		
		RunInstancesResult runInstancesResult = this.ec2.runInstances(request);
		List<WorkerInstanceHolder> newWorkers = new ArrayList<>();
		for (Instance instance : runInstancesResult.getReservation().getInstances()) {
			Log.i(LOG_TAG, String.format("Successfully started EC2 instance %s based on AMI %s", instance.getInstanceId(), WORKER_AMI_ID));
			newWorkers.add(new WorkerInstanceHolder(instance));
		}
		return newWorkers;
	}
	
	public void terminateInstance(WorkerInstanceHolder worker) {
		List<WorkerInstanceHolder> workers = new ArrayList<>();
		workers.add(worker);
		this.terminateInstances(workers);
	}
	
	public void terminateInstances(List<WorkerInstanceHolder> workers) {
		for (WorkerInstanceHolder worker : workers) {
			String instanceId = worker.getId();
			Log.i(LOG_TAG, "Terminating worker instance with id " + instanceId);
			TerminateInstancesRequest request = new TerminateInstancesRequest()
					.withInstanceIds(instanceId);
			this.ec2.terminateInstances(request);
			Log.i(LOG_TAG, String.format("Successfully terminated EC2 instance %s", instanceId));
		}
	}
	
	private void evaluation() {
		Map<WorkerInstanceHolder, Double> statistics = new HashMap<>();
		for (WorkerInstanceHolder worker : workers) {
			GetMetricDataResult getMetricDataResult = cw.getMetricData(getMetricDataRequest(worker));
			// there is only one metric data always
			MetricDataResult metricDataResult = getMetricDataResult.getMetricDataResults().get(0);
			for (int i = 0, len = metricDataResult.getValues().size(); i < len; i++) {
				Log.i(LOG_TAG, metricDataResult.getTimestamps().get(i) + " : " + metricDataResult.getValues().get(i));
			}
			
			double statistic = 0;
			switch (this.mode) {
				case MODE_AVERAGE:
					statistic = this.getAverage(metricDataResult.getValues());
					break;
				case MODE_MAXIMUM:
					statistic = this.getMaximum(metricDataResult.getValues());
					break;
				case MODE_MINIMUM:
					statistic = this.getMinimum(metricDataResult.getValues());
					break;
			}
			statistics.put(worker, statistic);
		}
		
		List<WorkerInstanceHolder> overloadWorkers = new ArrayList<>();
		for (Map.Entry<WorkerInstanceHolder, Double> entry : statistics.entrySet()) {
			if (entry.getValue() >= this.highThreshold)
				overloadWorkers.add(entry.getKey());
		}
		
		List<WorkerInstanceHolder> underloadWorkers = new ArrayList<>();
		for (Map.Entry<WorkerInstanceHolder, Double> entry : statistics.entrySet()) {
			if (entry.getValue() <= this.lowThreshold)
				underloadWorkers.add(entry.getKey());
		}
		
		if (overloadWorkers.size() > 0)
			this.evaluationCallback.createInstance(startInstance());
		if (underloadWorkers.size() > 0) {
			for (WorkerInstanceHolder worker : underloadWorkers)
				this.evaluationCallback.terminateInstance(worker);
		}
	}
	
	private GetMetricDataRequest getMetricDataRequest(WorkerInstanceHolder worker) {
		long currentTime = new Date().getTime();
		Dimension dimension = new Dimension()
				.withName("InstanceId")
				.withValue(worker.getId());
		Metric metric = new Metric()
				.withNamespace("AWS/EC2")
				.withMetricName("CPUUtilization")
				.withDimensions(dimension);
		MetricStat metricStat = new MetricStat()
				.withMetric(metric)
				.withPeriod(CW_PERIOD)
				.withStat("p100");
		MetricDataQuery metricDataQuery = new MetricDataQuery()
				.withMetricStat(metricStat)
				.withId("metricDataCPUUtilization");
		return new GetMetricDataRequest()
				.withStartTime(new Date(currentTime - (CW_OFFSET * 1000)))
				.withMetricDataQueries(metricDataQuery)
				.withScanBy("TimestampDescending")
				.withEndTime(new Date(currentTime));
	}
	
	private double getAverage(List<Double> values) {
		double statistic = 0;
		for (Double value : values)
			statistic += value;
		statistic = statistic / values.size();
		return statistic;
	}
	
	private double getMaximum(List<Double> values) {
		double statistic = Double.MIN_VALUE;
		for (Double value : values) {
			if (statistic < value)
				statistic = value;
		}
		return statistic;
	}
	
	private double getMinimum(List<Double> values) {
		double statistic = Double.MAX_VALUE;
		for (Double value : values) {
			if (statistic > value)
				statistic = value;
		}
		return statistic;
	}
}
