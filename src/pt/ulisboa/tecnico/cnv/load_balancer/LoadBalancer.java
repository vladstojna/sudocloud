package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import pt.ulisboa.tecnico.cnv.load_balancer.configuration.DynamoDBConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.PredictorConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.predictor.StochasticGradientDescent3D;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class LoadBalancer implements InstanceManager {

	private static final String LOG_TAG = LoadBalancer.class.getSimpleName();

	private final DynamoDBConfig dynamoDBConfig;
	private final WorkerInstanceConfig workerConfig;
	private final PredictorConfig predictorConfig;

	private final AmazonDynamoDB dynamoDB;

	private final Lock requestLock = new ReentrantLock();
	private final Condition requestCondition = requestLock.newCondition();

	private final ConcurrentSkipListSet<WorkerInstanceHolder> instances;
	private final ConcurrentMap<String, StochasticGradientDescent3D> predictors;

	public LoadBalancer(DynamoDBConfig dc, WorkerInstanceConfig wc, PredictorConfig pc) {
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

		instances = new ConcurrentSkipListSet<>(new WorkerInstanceHolder.BalancedComparator());
		predictors = new ConcurrentHashMap<>();

		dynamoDB = AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(dynamoDBConfig.getRegion())
			.build();

		createTableIfNotExists();

		Log.i(LOG_TAG, "initialized");
	}

	private void createTableIfNotExists() {
		CreateTableRequest createTableRequest = new CreateTableRequest()
			.withTableName(dynamoDBConfig.getTableName())
			.withKeySchema(new KeySchemaElement()
				.withAttributeName(dynamoDBConfig.getKeyName())
				.withKeyType(KeyType.HASH))
			.withAttributeDefinitions(new AttributeDefinition()
				.withAttributeName(dynamoDBConfig.getKeyName())
				.withAttributeType(ScalarAttributeType.S))
			.withProvisionedThroughput(new ProvisionedThroughput()
				.withReadCapacityUnits(dynamoDBConfig.getReadCapacity())
				.withWriteCapacityUnits(dynamoDBConfig.getWriteCapacity()));

		boolean result = TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
		if (result) {
			Log.i(LOG_TAG, "Created new table, did not exist previously");
		} else {
			Log.i(LOG_TAG, "Table already exists");
		}
		try {
			TableUtils.waitUntilActive(dynamoDB, dynamoDBConfig.getTableName());
		} catch (Exception e) {
			throw new AmazonClientException("Table creation error", e);
		}
	}

	public WorkerInstanceConfig getWorkerInstanceConfig() {
		return workerConfig;
	}

	/**
	 * Converts a raw key value into a dynamoDB key
	 * @param keyValue the raw key value
	 * @return dynamoDB key
	 */
	private Map<String, AttributeValue> getDynamoDBKey(String keyValue) {
		Map<String, AttributeValue> map = new HashMap<>();
		AttributeValue attributeValue = new AttributeValue().withS(keyValue);
		map.put(dynamoDBConfig.getKeyName(), attributeValue);
		return map;
	}

	/**
	 * Gets an item from dynamoDB.
	 * @param requestKey the raw key value
	 * @return the item or null if not found
	 */
	private Map<String, AttributeValue> getItem(String requestKey) {
		GetItemRequest getRequest = new GetItemRequest(
			dynamoDBConfig.getTableName(),
			getDynamoDBKey(requestKey));
		GetItemResult result = dynamoDB.getItem(getRequest);
		return result.getItem();
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
		Map<String, AttributeValue> items = getItem(request.getQuery());

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

	public WorkerInstanceHolder chooseInstance(Request request) throws InterruptedException {
		getAndUpdateCost(request);
		Log.i("RequestQueue", "Enqueued request " + request.getId());
		requestLock.lockInterruptibly();
		try {

			while (!instances.first().isAvailable()) {
				requestCondition.await();
			}

			WorkerInstanceHolder holder = instances.pollFirst();
			holder.addRequest(request);
			instances.add(holder);
			Log.i("RequestQueue", "Dequeued request " + request.getId());
			Log.i(LOG_TAG, "Instance chosen: " + holder);

			return holder;
		} finally {
			requestLock.unlock();
		}
	}

	public void removeRequest(WorkerInstanceHolder holder, Request request, AutoScaler callback) throws InterruptedException {
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
	public void markForRemoval(WorkerInstanceHolder holder, AutoScaler callback) throws InterruptedException {
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

}
