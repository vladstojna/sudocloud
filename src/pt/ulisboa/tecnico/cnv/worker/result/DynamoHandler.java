package pt.ulisboa.tecnico.cnv.worker.result;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DynamoHandler implements ResultHandler {

	private static final Log log = LogFactory.getLog(DynamoHandler.class);

	private final String tableName;
	private final AmazonDynamoDB dynamoDB;

	public DynamoHandler(String tableName, String region) throws AmazonClientException {
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
		this.dynamoDB = AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion(region)
			.build();
	}

	private Map<String, AttributeValue> newItem(MetricsResult data) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("query", new AttributeValue(data.getKey()));
		item.put("cost", new AttributeValue().withN(Long.toString(data.getCost())));

		return item;
	}

	@Override
	public void handle(MetricsResult data) {
		try {
			PutItemResult result = dynamoDB.putItem(tableName, newItem(data));
			log.info("Result: " + result);
		} catch (AmazonServiceException ase) {
			log.error("Error Message: " + ase.getMessage() +
				" | HTTP Status Code: " + ase.getStatusCode() +
				" | AWS Error Code: " + ase.getErrorCode() +
				" | Error Type: " + ase.getErrorType() +
				" | Request ID: " + ase.getRequestId());

		} catch (AmazonClientException ace) {
			log.error("Error Message: " + ace.getMessage());
		}
	}

}