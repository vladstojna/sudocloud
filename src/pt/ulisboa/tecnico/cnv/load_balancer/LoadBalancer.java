package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import pt.ulisboa.tecnico.cnv.load_balancer.heartbeat.HeartbeatInterface;
import pt.ulisboa.tecnico.cnv.load_balancer.heartbeat.HeartbeatManager;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.DynamoDBConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.PredictorConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.predictor.StochasticGradientDescent3D;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.DynamoDBUtils;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class LoadBalancer implements InstanceManager, HeartbeatInterface {

	private static final String LOG_TAG = LoadBalancer.class.getSimpleName();

	private final DynamoDBConfig dynamoDBConfig;
	private final WorkerInstanceConfig workerConfig;
	private final PredictorConfig predictorConfig;

	private final AmazonDynamoDB dynamoDB;

	private final Lock requestLock = new ReentrantLock();
	private final Condition requestCondition = requestLock.newCondition();

	private final ConcurrentSkipListSet<WorkerInstanceHolder> instances;
	private final ConcurrentMap<String, StochasticGradientDescent3D> predictors;

	private final AtomicLong pendingRequests;

	public LoadBalancer(AmazonDynamoDB dynamoDB, DynamoDBConfig dc, WorkerInstanceConfig wc, PredictorConfig pc) {
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();

		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
				"Cannot load the credentials from the credential profiles file. " +
				"Please make sure that your credentials file is at the correct " +
				"location (~/.aws/credentials), and is in valid format.", e);
		}

		dynamoDBConfig = dc;
		workerConfig = wc;
		predictorConfig = pc;

		this.dynamoDB = dynamoDB;

		instances = new ConcurrentSkipListSet<>(new WorkerInstanceHolder.BalancedComparator());
		predictors = new ConcurrentHashMap<>();
		pendingRequests = new AtomicLong();

		// initialize ping checking with instances
		new HeartbeatManager(this);

		Log.i(LOG_TAG, "initialized");
	}

	public WorkerInstanceConfig getWorkerInstanceConfig() {
		return workerConfig;
	}

	/**
	 * Gets or adds predictor from map
	 * @param key the map key
	 * @return the predictor found
	 */
	private StochasticGradientDescent3D getOrAddPredictor(String key) {
		StochasticGradientDescent3D predictor = predictors.get(key);
		if (predictor == null) {
			predictor = new StochasticGradientDescent3D(predictorConfig);
			StochasticGradientDescent3D prev = predictors.putIfAbsent(key, predictor);
			if (prev != null) {
				predictor = prev;
			}
		}
		return predictor;
	}

	/**
	 * Retrieves request cost from dynamoDB if it exists, otherwise
	 * tries to make a prediction. Updates the request cost.
	 * @param request the request for which to retrieve and update the cost
	 */
	private void getAndUpdateCost(Request request) {
		Map<String, AttributeValue> items = DynamoDBUtils
			.getItem(dynamoDB, dynamoDBConfig, request.getQuery());

		StochasticGradientDescent3D predictor = getOrAddPredictor(
			request.getQueryParameters().getSolverStrategy());

		long cost;

		if (items == null) {
			Log.i(LOG_TAG, "Not found - request " + request);
			synchronized (predictor) {
				cost = (long) predictor.getPrediction(request.getQueryParameters());
			}
			request.setCost(cost);
		} else {
			Log.i(LOG_TAG, "Found - request " + request);
			AttributeValue attrValue = items.get(dynamoDBConfig.getValueName());
			if (attrValue == null) {
				throw new RuntimeException("Item found but \"" + dynamoDBConfig.getValueName() + "\" not found!");
			}
			cost = Long.parseLong(items.get(dynamoDBConfig.getValueName()).getN());
			request.setCost(cost);
			synchronized (predictor) {
				predictor.feed(request);
			}
		}
		Log.i(LOG_TAG, "Updated cost for request " + request);
	}

	private boolean predictInstanceScalingNecessity() {
		long maxCapacity = instances.first().getMaxRequestCapacity();
		long threshold = 5 * maxCapacity;
		return pendingRequests.get() / (maxCapacity * instances.size()) >= threshold;
	}

	public WorkerInstanceHolder chooseInstance(Request request, InstanceScaling instScaling) throws InterruptedException {
		getAndUpdateCost(request);
		Log.i("RequestQueue", "Enqueued request " + request.getId() + ", pending " + pendingRequests.incrementAndGet());
		requestLock.lockInterruptibly();
		try {

			if (predictInstanceScalingNecessity()) {
				instScaling.attemptCreateInstanceAsync(this);
			}

			while (!instances.first().isAvailable()) {
				requestCondition.await();
			}

			WorkerInstanceHolder holder = instances.pollFirst();
			holder.addRequest(request);
			instances.add(holder);
			Log.i("RequestQueue", "Dequeued request " + request.getId() + ", pending " + pendingRequests.decrementAndGet());
			Log.i(LOG_TAG, "Instance chosen: " + holder);

			return holder;
		} finally {
			requestLock.unlock();
		}
	}

	public void removeRequest(WorkerInstanceHolder holder, Request request, InstanceScaling callback) throws InterruptedException {
		Log.i(LOG_TAG, "Remove request: " + request);
		requestLock.lockInterruptibly();
		try {

			instances.remove(holder);
			holder.removeRequest(request.getId());


			if (holder.canRemove()) {
				callback.terminateInstanceAsync(holder.getInstance());
			} else {
				instances.add(holder);
			}

			requestCondition.signal();

			Log.i(LOG_TAG, "Instance after removal: " + holder);
		} finally {
			requestLock.unlock();
		}
	}

	@Override
	public Iterable<WorkerInstanceHolder> getInstances() {
		return instances;
	}

	@Override
	public void addInstance(WorkerInstanceHolder holder) {
		instances.add(holder);
	}

	@Override
	public void markForRemoval(WorkerInstanceHolder holder, InstanceScaling callback) throws InterruptedException {
		requestLock.lockInterruptibly();
		try {
			holder.markForRemoval();
			instances.remove(holder);
			if (holder.canRemove()) {
				callback.terminateInstanceAsync(holder.getInstance());
			}
		} finally {
			requestLock.unlock();
		}

	}

	/**
	 * Handler for when worker's heartbeat is received
	 **/
	public void workerHeartbeat(String workerId) {
		Log.i(LOG_TAG, "Heartbeat from worker " + workerId);
	}

	public List<String> getWorkerIPs() {
		List<String> result = new ArrayList<String>();
		for (Iterator<WorkerInstanceHolder> instance = instances.iterator(); instance.hasNext();)
			result.add(instance.next().getPublicIpAddress());
		return result;
	}
}
