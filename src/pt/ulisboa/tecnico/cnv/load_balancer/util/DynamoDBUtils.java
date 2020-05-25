package pt.ulisboa.tecnico.cnv.load_balancer.util;

import java.util.HashMap;
import java.util.Map;

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

public class DynamoDBUtils {

	private static final String LOG_TAG = DynamoDBUtils.class.getSimpleName();

	public static AmazonDynamoDB createClient(DynamoDBConfig dynamoDBConfig) {
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();

		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
				"Cannot load the credentials from the credential profiles file. " +
				"Please make sure that your credentials file is at the correct " +
				"location (~/.aws/credentials), and is in valid format.", e);
		}

		return AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(dynamoDBConfig.getRegion())
			.build();
	}

	/**
	 * Creates a table if it does not exist and waits until it is active
	 * @param dynamoDB the client
	 * @param dynamoDBConfig the configuration
	 */
	public static void createTableIfNotExists(AmazonDynamoDB dynamoDB, DynamoDBConfig dynamoDBConfig) {
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
	 * Converts a raw key value into a dynamoDB key
	 * @param keyValue the raw key value
	 * @return dynamoDB key
	 */
	private static Map<String, AttributeValue> getDynamoDBKey(DynamoDBConfig dynamoDBConfig, String keyValue) {
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
	public static Map<String, AttributeValue> getItem(AmazonDynamoDB dynamoDB, DynamoDBConfig dynamoDBConfig, String requestKey) {
		GetItemRequest getRequest = new GetItemRequest(
			dynamoDBConfig.getTableName(),
			getDynamoDBKey(dynamoDBConfig, requestKey));
		GetItemResult result = dynamoDB.getItem(getRequest);
		return result.getItem();
	}

}
