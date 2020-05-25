package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
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

	private final InstanceManager instanceManager;
	private final MetricType metricType;

	public AutoScaler(AutoScalerConfig asc, WorkerInstanceConfig wc, InstanceManager im, MetricType mode) {

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

		this.instanceManager = im;
		this.metricType = mode;
	}

	public WorkerInstanceHolder startInstance() {
		return this.startInstances(1).get(0);
	}

	/**
	 * Await warmup time
	 * @throws InterruptedException if thread is interrupted
	 */
	private void awaitWarmup() throws InterruptedException {
		Log.i(LOG_TAG, "Waiting for warmup...");
		autoScalerConfig.getTimeUnit().sleep(autoScalerConfig.getWarmupPeriod());
	}

	/**
	 * Find the running or stopped worker instances and registers them.
	 * Starts stopped instances.
	 * If no instances found, creates new ones.
	 * Awaits the warmup time if necessary.
	 * Workers are tagged with the tag "type:worker"
	 * @throws InterruptedException if thread is interrupted
	 */
	public void initialInstanceStartup() throws InterruptedException {
		Log.i(LOG_TAG, "Initial worker instance lookup");

		Filter tagFilter = new Filter("tag:" + workerConfig.getTagKey())
			.withValues(workerConfig.getTagValue());
		Filter statusFilter = new Filter("instance-state-name")
			.withValues("running", "stopped");

		DescribeInstancesRequest request = new DescribeInstancesRequest()
			.withFilters(tagFilter, statusFilter);

		// Find the running instances
		DescribeInstancesResult response = ec2.describeInstances(request);

		int minInstances = autoScalerConfig.getMinInstances();

		// If no worker instances, create how many necessary
		if (response.getReservations().isEmpty()) {
			List<Instance> instances = createInstances(minInstances);
			Log.i(LOG_TAG, "No worker instances found, created " + minInstances);
			awaitWarmup();
			for (Instance i : instances) {
				instanceManager.addInstance(new WorkerInstanceHolder(i));
			}
		}

		Set<Instance> instances = new HashSet<>();
		for (Reservation r : response.getReservations()) {
			for (Instance i : r.getInstances()) {
				instances.add(i);
			}
		}

		boolean mustAwaitWarmup = false;

		// if too many initial instances, terminate some
		if (instances.size() > minInstances) {
			Log.i(LOG_TAG, "Too many instances exist (" + instances.size() + "/" + minInstances + ")");
			List<Instance> instancesToTerminate = new ArrayList<>(instances.size() - minInstances);
			Iterator<Instance> iter = instances.iterator();
			for (int i = 0; i < instances.size() - minInstances; i++) {
				instancesToTerminate.add(iter.next());
			}
			terminateInstances(instancesToTerminate);
			instances.removeAll(instancesToTerminate);
		// if too few instances, create some
		} else if (instances.size() < minInstances) {
			Log.i(LOG_TAG, "Too few instances exist (" + instances.size() + "/" + minInstances + ")");
			List<Instance> createdInstances = createInstances(minInstances - instances.size());
			instances.addAll(createdInstances);
			mustAwaitWarmup = true;
		}

		// start stopped instances
		StartInstancesRequest startRequest = new StartInstancesRequest();
		for (Instance i : instances) {
			if (i.getState().getName().equals("stopped")) {
				Log.i(LOG_TAG, "Found STOPPED worker instance with id " + i.getInstanceId());
				mustAwaitWarmup = true;
				startRequest.withInstanceIds(i.getInstanceId());
			} else {
				Log.i(LOG_TAG, "Found RUNNING worker instance with id " + i.getInstanceId());
			}
		}

		if (!startRequest.getInstanceIds().isEmpty())
			ec2.startInstances(startRequest);

		if (mustAwaitWarmup) {
			awaitWarmup();
		}
		for (Instance i : instances) {
			instanceManager.addInstance(new WorkerInstanceHolder(i));
		}
	}

	private void terminateInstances(Collection<Instance> instances) {
		Log.i(LOG_TAG, "Terminating " + instances.size() + " instances");
		TerminateInstancesRequest request = new TerminateInstancesRequest();
		for (Instance i : instances)
				request.withInstanceIds(i.getInstanceId());
		ec2.terminateInstances(request);
	}

	private List<Instance> createInstances(int quantity) {
		RunInstancesRequest runRequest = new RunInstancesRequest();
		runRequest.withImageId(workerConfig.getImageId())
			.withTagSpecifications(new TagSpecification()
				.withResourceType(ResourceType.Instance)
				.withTags(new Tag(workerConfig.getTagKey(), workerConfig.getTagValue())))
			.withInstanceType(workerConfig.getType())
			.withMinCount(quantity)
			.withMaxCount(quantity)
			.withKeyName(workerConfig.getKeyName())
			.withSecurityGroups(workerConfig.getSecurityGroup());
		RunInstancesResult runResult = ec2.runInstances(runRequest);
		return runResult.getReservation().getInstances();
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
		// this.terminateInstances(workers);
	}

	/*
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
	*/

	private void evaluation() {
		Map<WorkerInstanceHolder, Double> statistics = new HashMap<>();
		for (WorkerInstanceHolder worker : instanceManager.getInstances()) {
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
