package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

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
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagSpecification;

import pt.ulisboa.tecnico.cnv.load_balancer.configuration.DynamoDBConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.PredictorConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.predictor.StochasticGradientDescent3D;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class LoadBalancer {

	private static final String LOG_TAG = LoadBalancer.class.getSimpleName();

	private final DynamoDBConfig dynamoDBConfig;
	private final WorkerInstanceConfig workerConfig;
	private final PredictorConfig predictorConfig;

	private final AmazonDynamoDB dynamoDB;
	private final AmazonEC2 ec2;

	private final Object skipListLock = new Object();
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
		instances = new ConcurrentSkipListSet<>(new WorkerInstanceHolder.TotalCostComparator());
		predictors = new ConcurrentHashMap<>();

		ec2 = AmazonEC2ClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(workerConfig.getRegion())
			.build();

		dynamoDB = AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(dynamoDBConfig.getRegion())
			.build();

		createTableIfNotExists();
		getOrCreateWorkerInstances();

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

	/**
	 * Find the running or stopped worker instances and registers them.
	 * Starts stopped instances.
	 * If no instances found, create a new one.
	 * They are tagged with the tag "type:worker"
	 **/
	private void getOrCreateWorkerInstances() {
		Log.i(LOG_TAG, "Initial worker instance lookup");

		Filter tagFilter = new Filter("tag:" + workerConfig.getTagKey())
			.withValues(workerConfig.getTagValue());
		Filter statusFilter = new Filter("instance-state-name")
			.withValues("running", "stopped");

		DescribeInstancesRequest request = new DescribeInstancesRequest()
			.withFilters(tagFilter, statusFilter);

		// Find the running instances
		DescribeInstancesResult response = ec2.describeInstances(request);

		// If no worker instances, create one
		if (response.getReservations().isEmpty()) {
			RunInstancesRequest runRequest = new RunInstancesRequest();
			runRequest.withImageId(workerConfig.getImageId())
				.withTagSpecifications(new TagSpecification()
					.withResourceType("instance")
					.withTags(new Tag(workerConfig.getTagKey(), workerConfig.getTagValue())))
				.withInstanceType(workerConfig.getType())
				.withMinCount(1)
				.withMaxCount(1)
				.withKeyName(workerConfig.getKeyName())
				.withSecurityGroups(workerConfig.getSecurityGroup());
			RunInstancesResult runResult = ec2.runInstances(runRequest);
			Instance inst = runResult.getReservation().getInstances().get(0);
			instances.add(new WorkerInstanceHolder(inst));
			Log.i(LOG_TAG, "No worker instances found, created one with id " + inst.getInstanceId());
			return;
		}

		List<String> stoppedInstanceIds = new ArrayList<>();
		for (Reservation reservation : response.getReservations()) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getState().getName().equals("stopped")) {
					stoppedInstanceIds.add(instance.getInstanceId());
					Log.i(LOG_TAG, "Found STOPPED worker instance with id " + instance.getInstanceId());
				} else {
					Log.i(LOG_TAG, "Found RUNNING worker instance with id " + instance.getInstanceId());
				}
				instances.add(new WorkerInstanceHolder(instance));
			}
		}

		if (!stoppedInstanceIds.isEmpty()) {
			Log.i(LOG_TAG, "Starting stopped instances");
			StartInstancesRequest startRequest = new StartInstancesRequest();
			startRequest.withInstanceIds(stoppedInstanceIds);
			ec2.startInstances(startRequest);
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

	public void addInstance(Instance instance) {
		instances.add(new WorkerInstanceHolder(instance));
	}

	public WorkerInstanceHolder chooseInstance(Request request) {
		Log.i(LOG_TAG, "Choose instance for request: " + request);
		getAndUpdateCost(request);
		synchronized (skipListLock) {
			WorkerInstanceHolder holder = instances.pollFirst();
			holder.addRequest(request);
			instances.add(holder);
			Log.i(LOG_TAG, "Instance chosen: " + holder);
			return holder;
		}
	}

	public void removeRequest(WorkerInstanceHolder holder, Request request) {
		Log.i(LOG_TAG, "Remove request: " + request);
		synchronized (skipListLock) {
			instances.remove(holder);
			holder.removeRequest(request.getId());
			instances.add(holder);
			Log.i(LOG_TAG, "Instance after removal: " + holder);
		}
	}

}
