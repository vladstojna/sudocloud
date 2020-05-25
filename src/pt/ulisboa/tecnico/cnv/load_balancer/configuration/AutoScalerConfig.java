package pt.ulisboa.tecnico.cnv.load_balancer.configuration;

import java.util.concurrent.TimeUnit;

public class AutoScalerConfig {

	private final int pollingPeriod;
	private final int minInstances;
	private final int maxInstances;
	private final int warmupPeriod;
	private final int minCpuUsage;
	private final int maxCpuUsage;
	private final int cloudWatchPeriod;
	private final int cloudWatchOffset;
	private final String region;
	private final TimeUnit timeUnit;

	public AutoScalerConfig(int pollingPeriod, int minInstances, int maxInstances, int warmupPeriod,
			int minCpuUsage, int maxCpuUsage, int cloudWatchPeriod, int cloudWatchOffset,
			String region, TimeUnit timeUnit) {
		this.pollingPeriod = pollingPeriod;
		this.minInstances = minInstances;
		this.maxInstances = maxInstances;
		this.warmupPeriod = warmupPeriod;
		this.minCpuUsage = minCpuUsage;
		this.maxCpuUsage = maxCpuUsage;
		this.cloudWatchPeriod = cloudWatchPeriod;
		this.cloudWatchOffset = cloudWatchOffset;
		this.region = region;
		this.timeUnit = timeUnit;
	}

	public int getMinInstances() {
		return minInstances;
	}

	public int getMaxInstances() {
		return maxInstances;
	}

	public int getWarmupPeriod() {
		return warmupPeriod;
	}

	public int getMinCpuUsage() {
		return minCpuUsage;
	}

	public int getMaxCpuUsage() {
		return maxCpuUsage;
	}

	public String getRegion() {
		return region;
	}

	public int getCloudWatchPeriod() {
		return cloudWatchPeriod;
	}

	public int getCloudWatchOffset() {
		return cloudWatchOffset;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	public int getPollingPeriod() {
		return pollingPeriod;
	}

}
