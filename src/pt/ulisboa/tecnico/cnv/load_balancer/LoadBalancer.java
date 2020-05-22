package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkClientException;
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

public class LoadBalancer {

	private static final String LOG_TAG = LoadBalancer.class.getSimpleName();

	private final String tableName;
	private final AmazonDynamoDB dynamoDB;
	private final AmazonEC2 ec2;

	// FIXME improve datastructure
	public List<Request> runningRequests = new ArrayList<>();
	private static List<WorkerInstanceHolder> workerInstances = new ArrayList<>();

	public LoadBalancer(String tableName, String region) {
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
				"Cannot load the credentials from the credential profiles file. " +
				"Please make sure that your credentials file is at the correct " +
				"location (~/.aws/credentials), and is in valid format.", e);
		}
		this.tableName = tableName;
		this.ec2 = AmazonEC2ClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(region)
			.build();
		this.dynamoDB = AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(region)
			.build();
		createTableIfNotExists();
		// initRunningWorkerInstances();
		Log.i(LOG_TAG, "Loadbalancer initialized");
	}

	private void createTableIfNotExists() {
		CreateTableRequest createTableRequest = new CreateTableRequest()
			.withTableName(tableName)
			.withKeySchema(new KeySchemaElement()
				.withAttributeName("query")
				.withKeyType(KeyType.HASH))
			.withAttributeDefinitions(new AttributeDefinition()
				.withAttributeName("query")
				.withAttributeType(ScalarAttributeType.S))
			.withProvisionedThroughput(new ProvisionedThroughput()
				.withReadCapacityUnits(10L)
				.withWriteCapacityUnits(10L));

		TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
		try {
			TableUtils.waitUntilActive(dynamoDB, tableName);
		} catch (Exception e) {
			throw new AmazonClientException("Table creation error", e);
		}
	}

	/**
	 * Obtains the instance that should be used for the next operation
	 **/
	public WorkerInstanceHolder getWorkerInstance() {
		// FIXME add logic of choice of workerInstance
		return workerInstances.get(0);
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

	/**
	 * Find the worker instances and adds them to
	 *
	 * They are tagged with the tag "type:worker"
	 **/
	private void initRunningWorkerInstances() {
		Log.i(LOG_TAG, "Initial worker instance lookup");
		try {
			//Create the Filter to use to find running instances
			Filter tag_filter = new Filter("tag:type");
			tag_filter.withValues("worker");

			Filter status_filter = new Filter("instance-state-name")
			.withValues("running");

			//Create a DescribeInstancesRequest
			DescribeInstancesRequest request = new DescribeInstancesRequest();
			request.withFilters(tag_filter, status_filter);

			// Find the running instances
			DescribeInstancesResult response = ec2.describeInstances(request);

			for (Reservation reservation : response.getReservations()) {
				for (Instance instance : reservation.getInstances()) {
					workerInstances.add(new WorkerInstanceHolder(instance));
					Log.i(LOG_TAG, "Found worker instance with id " +
							instance.getInstanceId());
				}
			}

		} catch (SdkClientException e) {
			e.getStackTrace();
		}

		if (workerInstances.size() == 0) {
			Log.i(LOG_TAG, "No running worker instance was found");
			Log.i(LOG_TAG, "Maybe you forgot to tag them with 'type:worker'");
		}
	}


	public void addInstance(WorkerInstanceHolder instance) {
		workerInstances.add(instance);
	}
}
