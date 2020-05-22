package pt.ulisboa.tecnico.cnv.load_balancer.configuration;

public class WorkerInstanceConfig {

	private final int port;
	private final String imageId;
	private final String type;
	private final String region;
	private final String tagKey;
	private final String tagValue;
	private final String keyName;
	private final String securityGroup;

	public WorkerInstanceConfig(int port, String imageId, String type, String region, String tagKey,
			String tagValue, String keyName, String securityGroup) {
		this.port = port;
		this.imageId = imageId;
		this.type = type;
		this.region = region;
		this.tagKey = tagKey;
		this.tagValue = tagValue;
		this.keyName = keyName;
		this.securityGroup = securityGroup;
	}

	public int getPort() {
		return port;
	}

	public String getImageId() {
		return imageId;
	}

	public String getType() {
		return type;
	}

	public String getRegion() {
		return region;
	}

	public String getTagKey() {
		return tagKey;
	}

	public String getTagValue() {
		return tagValue;
	}

	public String getKeyName() {
		return keyName;
	}

	public String getSecurityGroup() {
		return securityGroup;
	}

}
