package pt.ulisboa.tecnico.cnv.load_balancer.configuration;

public class DynamoDBConfig {

	private final String tableName;
	private final String region;
	private final String keyName;
	private final String valueName;
	private final long readCapacity;
	private final long writeCapacity;

	public DynamoDBConfig(String tableName, String region, String keyName, String valueName,
			long readCapacity, long writeCapacity) {
		this.tableName = tableName;
		this.region = region;
		this.keyName = keyName;
		this.valueName = valueName;
		this.readCapacity = readCapacity;
		this.writeCapacity = writeCapacity;
	}

	public String getTableName() {
		return tableName;
	}

	public String getRegion() {
		return region;
	}

	public String getKeyName() {
		return keyName;
	}

	public String getValueName() {
		return valueName;
	}

	public long getReadCapacity() {
		return readCapacity;
	}

	public long getWriteCapacity() {
		return writeCapacity;
	}

}
