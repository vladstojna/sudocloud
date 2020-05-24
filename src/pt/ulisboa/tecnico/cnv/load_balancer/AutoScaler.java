package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import pt.ulisboa.tecnico.cnv.load_balancer.callback.IEvaluationCallback;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.AutoScalerConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric.MetricType;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class AutoScaler {

	private static final String LOG_TAG = AutoScaler.class.getSimpleName();

	private final WorkerInstanceConfig workerConfig;
	private final AutoScalerConfig autoScalerConfig;

	// important to create and terminate instances
	private final AmazonEC2 ec2;
	// important for statistics
	private final AmazonCloudWatch cw;

	//private final IEvaluationCallback evaluationCallback;

	private final Iterable<WorkerInstanceHolder> workers;
	private final MetricType metricType;

	public AutoScaler(AutoScalerConfig asc, WorkerInstanceConfig wc, Iterable<WorkerInstanceHolder> workers, MetricType mode) {

		this.workerConfig = wc;
		this.autoScalerConfig = asc;

		this.ec2 = AmazonEC2ClientBuilder
				.standard()
				.withRegion(workerConfig.getRegion())
				.build();
		this.cw = AmazonCloudWatchClientBuilder
				.standard()
				.withRegion(autoScalerConfig.getRegion())
				.build();

		// this.evaluationCallback = evaluationCallback;

		this.workers = workers;
		this.metricType = mode;
	}

	public WorkerInstanceHolder startInstance() {
		return this.startInstances(1).get(0);
	}

	public List<WorkerInstanceHolder> startInstances(int numberInstances) {
		Log.i(LOG_TAG, "Starting " + numberInstances + " new worker instance");
		Tag tag = new Tag()
				.withKey(workerConfig.getTagKey())
				.withValue(workerConfig.getTagValue());
		TagSpecification tagSpecification = new TagSpecification()
				.withTags(tag)
				.withResourceType(ResourceType.Instance);
		RunInstancesRequest request = new RunInstancesRequest()
				.withImageId(workerConfig.getImageId())
				.withInstanceType(workerConfig.getType())
				.withMaxCount(numberInstances)
				.withMinCount(numberInstances)
				.withKeyName(workerConfig.getKeyName())
				.withSecurityGroups(workerConfig.getSecurityGroup())
				.withMonitoring(true)
				.withTagSpecifications(tagSpecification);

		RunInstancesResult runInstancesResult = this.ec2.runInstances(request);
		List<WorkerInstanceHolder> newWorkers = new ArrayList<>();
		for (Instance instance : runInstancesResult.getReservation().getInstances()) {
			Log.i(LOG_TAG, String.format("Successfully started EC2 instance %s based on AMI %s", instance.getInstanceId(), workerConfig.getImageId()));
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
			String instanceId = worker.getInstance().getInstanceId();
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
			statistics.put(worker, metricType.calculate(metricDataResult.getValues()));
		}

		List<WorkerInstanceHolder> overloadWorkers = new ArrayList<>();
		for (Map.Entry<WorkerInstanceHolder, Double> entry : statistics.entrySet()) {
			if (entry.getValue() >= autoScalerConfig.getMaxCpuUsage())
				overloadWorkers.add(entry.getKey());
		}

		List<WorkerInstanceHolder> underloadWorkers = new ArrayList<>();
		for (Map.Entry<WorkerInstanceHolder, Double> entry : statistics.entrySet()) {
			if (entry.getValue() <= autoScalerConfig.getMinCpuUsage())
				underloadWorkers.add(entry.getKey());
		}

		/* FIXME Commented, for now
		if (overloadWorkers.size() > 0)
			this.evaluationCallback.createInstance(startInstance());
		if (underloadWorkers.size() > 0) {
			for (WorkerInstanceHolder worker : underloadWorkers)
				this.evaluationCallback.terminateInstance(worker);
		}
		*/
	}

	private GetMetricDataRequest getMetricDataRequest(WorkerInstanceHolder worker) {
		long currentTime = new Date().getTime();
		Dimension dimension = new Dimension()
				.withName("InstanceId")
				.withValue(worker.getInstance().getInstanceId());
		Metric metric = new Metric()
				.withNamespace("AWS/EC2")
				.withMetricName("CPUUtilization")
				.withDimensions(dimension);
		MetricStat metricStat = new MetricStat()
				.withMetric(metric)
				.withPeriod(autoScalerConfig.getCloudWatchPeriod())
				.withStat("p100");
		MetricDataQuery metricDataQuery = new MetricDataQuery()
				.withMetricStat(metricStat)
				.withId("metricDataCPUUtilization");
		return new GetMetricDataRequest()
				.withStartTime(new Date(currentTime - (autoScalerConfig.getCloudWatchOffset() * 1000)))
				.withMetricDataQueries(metricDataQuery)
				.withScanBy("TimestampDescending")
				.withEndTime(new Date(currentTime));
	}

}
