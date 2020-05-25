package pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric;

import java.util.Collections;
import java.util.List;

public class Percentile implements MetricType {

	private final double percentile;

	public Percentile(double percentile) {
		this.percentile = percentile;
	}

	@Override
	public Double calculate(List<Double> metrics) {
		Collections.sort(metrics);
		int index = (int) Math.ceil(percentile * metrics.size()) - 1;
		return metrics.get(index);
	}

	@Override
	public String toString() {
		return "Percentile (" + percentile + ")";
	}
}
