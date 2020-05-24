package pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric;

import java.util.List;

public interface MetricType {

	Double calculate(List<Double> metrics);

}
