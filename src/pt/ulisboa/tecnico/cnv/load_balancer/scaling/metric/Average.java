package pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric;

import java.util.List;

public class Average implements MetricType {

	@Override
	public Double calculate(List<Double> metrics) {
		int denom = metrics.size() == 0 ? 1 : metrics.size();
		double statistic = 0;
		for (Double value : metrics)
			statistic += value;
		return statistic / denom;
	}

	@Override
	public String toString() {
		return "Average";
	}

}
