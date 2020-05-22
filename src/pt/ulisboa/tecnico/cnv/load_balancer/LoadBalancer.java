package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
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
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;

public class LoadBalancer {

	private static final String LOG_TAG = LoadBalancer.class.getSimpleName();

	private final DynamoDBConfig dynamoDBConfig;
	private final WorkerInstanceConfig workerConfig;

	private final AmazonDynamoDB dynamoDB;
	private final AmazonEC2 ec2;

	// FIXME improve datastructure
	public List<Request> runningRequests = new ArrayList<>();

	// FIXME: temporary boolean type just to hold instance types
	private final Map<Instance, Boolean> instances;

	public LoadBalancer(DynamoDBConfig dc, WorkerInstanceConfig wc) {
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
		instances = new ConcurrentHashMap<>();

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

		Log.i(LOG_TAG, "Loadbalancer initialized");
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

		TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
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
			instances.put(inst, true);
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
				instances.put(instance, true);
			}
		}

		if (!stoppedInstanceIds.isEmpty()) {
			StartInstancesRequest startRequest = new StartInstancesRequest();
			startRequest.withInstanceIds(stoppedInstanceIds);
			ec2.startInstances(startRequest);
		}
	}

	/**
	 * Informs the loadbalancer that a request has started processing
	 **/
	public void startedProcessing(Request request) {
		Log.i(LOG_TAG, String.format("Added request '%d to list of processing requests", request.getId()));
		runningRequests.add(request);
		Log.i(LOG_TAG, String.format("Currently running %d requests", runningRequests.size()));
	}

	/**
	 * Informs the loadbalancer that the request has finished processing
	 **/
	public void finishedProcessing(Request request) {
		Log.i(LOG_TAG, String.format("Added request '%d0 to list of processing requests", request.getId()));
		runningRequests.remove(request);
		Log.i(LOG_TAG, String.format("Currently running %d requests", runningRequests.size()));
	}

	public void addInstance(Instance instance) {
		instances.put(instance, true);
	}

}
