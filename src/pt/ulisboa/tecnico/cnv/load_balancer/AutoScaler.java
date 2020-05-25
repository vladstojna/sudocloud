package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

import pt.ulisboa.tecnico.cnv.load_balancer.configuration.AutoScalerConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric.MetricType;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class AutoScaler {

	private static final String LOG_TAG = AutoScaler.class.getSimpleName();

	private final ScheduledExecutorService cloudWatchExecutor;
	private final ExecutorService instanceExecutor;

	private final WorkerInstanceConfig workerConfig;
	private final AutoScalerConfig autoScalerConfig;

	private final AmazonEC2 ec2;
	private final AmazonCloudWatch cw;

	private final InstanceManager instanceManager;
	private final AtomicInteger currentInstanceCount;
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
		this.currentInstanceCount = new AtomicInteger();
		this.metricType = mode;
		this.cloudWatchExecutor = Executors.newScheduledThreadPool(1);
		this.instanceExecutor = Executors.newSingleThreadExecutor();
	}

	/**
	 * Starts the auto-scaling service
	 */
	public void start() {
		int delay = autoScalerConfig.getCloudWatchOffset();
		int period = autoScalerConfig.getPollingPeriod();
		TimeUnit timeUnit = autoScalerConfig.getTimeUnit();

		Log.i(LOG_TAG, "Started auto-scaling service. Will execute in " +
			delay + " " + timeUnit.toString() + " every " +
			period + " " + timeUnit.toString());

		cloudWatchExecutor.scheduleAtFixedRate(new EvaluationRunnable(), delay, period, timeUnit);
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
			return;
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

		currentInstanceCount.addAndGet(instances.size());
		for (Instance i : instances) {
			instanceManager.addInstance(new WorkerInstanceHolder(i));
		}
	}

	/**
	 * Terminate instances
	 * @param instances instances to terminate
	 */
	private void terminateInstances(Collection<Instance> instances) {

		if (instances == null) {
			throw new NullPointerException("instances cannot be null");
		}

		if (instances.isEmpty()) {
			throw new IllegalArgumentException("instances cannot be empty");
		}

		if (isAtMin()) {
			Log.i(LOG_TAG, "Minimum instance count reached");
			return;
		}

		Log.i(LOG_TAG, "Terminating " + instances.size() + " instance(s)");
		TerminateInstancesRequest request = new TerminateInstancesRequest();
		for (Instance i : instances)
				request.withInstanceIds(i.getInstanceId());
		ec2.terminateInstances(request);

		currentInstanceCount.addAndGet(-instances.size());
	}

	/**
	 * Create instances
	 * @param quantity the amount of instances to create
	 * @return list of created instances or null if at max quantity
	 */
	private List<Instance> createInstances(int quantity) {

		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be a positive number");
		}

		if (isAtMax()) {
			Log.i(LOG_TAG, "Maximum instance count reached");
			return null;
		}

		Log.i(LOG_TAG, "Creating " + quantity + " instances...");

		RunInstancesRequest runRequest = new RunInstancesRequest();
		runRequest.withImageId(workerConfig.getImageId())
			.withTagSpecifications(new TagSpecification()
				.withResourceType(ResourceType.Instance)
				.withTags(new Tag(workerConfig.getTagKey(), workerConfig.getTagValue())))
			.withInstanceType(workerConfig.getType())
			.withMinCount(quantity)
			.withMaxCount(quantity)
			.withKeyName(workerConfig.getKeyName())
			.withSecurityGroups(workerConfig.getSecurityGroup())
			.withMonitoring(true);
		RunInstancesResult runResult = ec2.runInstances(runRequest);

		currentInstanceCount.addAndGet(quantity);

		return runResult.getReservation().getInstances();
	}

	/**
	 * Creates one instance
	 * @return the instance created or null if instance was not created
	 */
	private Instance createInstance() {
		List<Instance> instances = createInstances(1);
		return instances == null ? null : instances.get(0);
	}

	public void createInstanceAsync(InstanceManager callback) {
		instanceExecutor.execute(new Runnable() {

			@Override
			public void run() {
				Instance instance = createInstance();
				if (instance != null) {
					try {
						awaitWarmup();
						callback.addInstance(new WorkerInstanceHolder(instance));
					} catch (InterruptedException e) {
						terminateInstance(instance);
						Log.e(LOG_TAG, e);
					}
				}
			}
		});
	}

	/**
	 * Terminate one instance
	 * @param instance the instance to terminate
	 */
	public void terminateInstance(Instance instance) {
		terminateInstances(Arrays.asList(instance));
	}

	public void terminateInstanceAsync(Instance instance) {
		instanceExecutor.execute(new Runnable(){

			@Override
			public void run() {
				try {
					terminateInstance(instance);
				} catch (Exception e) {
					Log.e(LOG_TAG, "Unable to terminate instance " + instance.getInstanceId(), e);
				}
			}
		});
	}

	private boolean hasOverflowed() {
		return currentInstanceCount.get() > autoScalerConfig.getMaxInstances();
	}

	private boolean hasUnderflowed() {
		return currentInstanceCount.get() < autoScalerConfig.getMinInstances();
	}

	private boolean isAtMax() {
		return currentInstanceCount.get() == autoScalerConfig.getMaxInstances();
	}

	private boolean isAtMin() {
		return currentInstanceCount.get() == autoScalerConfig.getMinInstances();
	}

	private boolean isOverloaded(Double data) {
		return data >= autoScalerConfig.getMaxCpuUsage();
	}

	private boolean isUnderloaded(Double data) {
		return data <= autoScalerConfig.getMinCpuUsage();
	}

	private int minMetricSize() {
		return autoScalerConfig.getCloudWatchOffset() / autoScalerConfig.getCloudWatchPeriod() - 1;
	}

	private GetMetricDataRequest getMetricDataRequest(Instance instance) {
		long currentTime = new Date().getTime();
		Dimension dimension = new Dimension()
				.withName("InstanceId")
				.withValue(instance.getInstanceId());
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

	private class EvaluationRunnable implements Runnable {

		private class Entry implements Comparable<Entry> {
			private final WorkerInstanceHolder holder;
			private final Double metricResult;

			public Entry(WorkerInstanceHolder holder, Double metricResult) {
				this.holder = holder;
				this.metricResult = metricResult;
			}

			@Override
			public int compareTo(Entry o) {
				int i = Double.compare(metricResult, o.metricResult);
				if (i == 0) {
					i = holder.compareTo(o.holder);
				}
				return i;
			}
		}

		private void evaluation() {

			if (hasOverflowed()) {
				throw new IllegalStateException("There are more instances than the maximum!");
			}

			if (hasUnderflowed()) {
				throw new IllegalStateException("There are less instances than the minimum!");
			}

			Set<Entry> statistics = new TreeSet<>();

			for (WorkerInstanceHolder holder : instanceManager.getInstances()) {

				GetMetricDataResult getMetricDataResult = cw.getMetricData(
					getMetricDataRequest(holder.getInstance()));

				// there is only one metric data always
				MetricDataResult metricDataResult = getMetricDataResult.getMetricDataResults().get(0);

				for (int i = 0, len = metricDataResult.getValues().size(); i < len; i++) {
					Log.i(LOG_TAG, metricDataResult.getTimestamps().get(i) + " : " +
						metricDataResult.getValues().get(i) + "%");
				}

				List<Double> values = metricDataResult.getValues();
				int minSize = minMetricSize();
				if (values.isEmpty() || values.size() < minSize) {
					Log.i(LOG_TAG, holder.getInstance().getInstanceId() +
						" : only " + values.size() + "/" + minSize + " metric(s), not considering");
				} else {
					double result = metricType.calculate(values);
					statistics.add(new Entry(holder, result));
					Log.i(LOG_TAG, holder.getInstance().getInstanceId() +
						" : " + values.size() + " metric(s) collected (" +
						metricType + " = " + result + ")");
				}

			}

			if (statistics.isEmpty())
				return;

			int overloadedWorkers = 0;
			List<Entry> underloadedWorkers = new ArrayList<>();

			for (Entry entry : statistics) {
				if (isOverloaded(entry.metricResult)) {
					overloadedWorkers++;
				} else if (isUnderloaded(entry.metricResult)) {
					underloadedWorkers.add(entry);
				}
			}

			if (overloadedWorkers > 0 && !underloadedWorkers.isEmpty()) {
				Log.i(LOG_TAG, "Workload imbalance may be present; both underloaded and overloaded workers exist");
			} else if (overloadedWorkers > 0) {
				Log.i(LOG_TAG, "Overloaded workers found");
			} else if (!underloadedWorkers.isEmpty()) {
				Log.i(LOG_TAG, "Underloaded workers found");
			} else {
				Log.i(LOG_TAG, "Workload seems balanced; no underloaded or overloaded workers found");
			}

			scale(overloadedWorkers, underloadedWorkers);
		}

		/**
		 * Scaling policy.
		 * Places faith in the load balancer to balance the load when there
		 * is the same quantity of under/overloaded instances.
		 * When scaling up, only considers that overloaded instances must outnumber
		 * the other ones.
		 * When scaling down, marks the least loaded instance from the underloaded
		 * instances list (first one, since it is ordered by CPU load and total cost)
		 * @param overloadedInstances number of overloaded workers
		 * @param underloadedInstances underloaded instances list
		 */
		private void scale(int overloadedInstances, List<Entry> underloadedInstances) {
			if (overloadedInstances > underloadedInstances.size()) {
				createInstanceAsync(instanceManager);
			} else if (underloadedInstances.size() > overloadedInstances) {
				if (!isAtMin()) {
					instanceManager.markForRemoval(underloadedInstances.get(0).holder);
				} else {
					Log.e(LOG_TAG, "Unable to mark instance for removal: already at minimum count");
				}
			} else {
				Log.i(LOG_TAG, "Same # of under/overloaded workers, hope the load balancer will balance it out");
			}
		}

		@Override
		public void run() {
			try {
				evaluation();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

}
