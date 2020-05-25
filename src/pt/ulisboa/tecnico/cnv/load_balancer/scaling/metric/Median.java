package pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric;

public class Median extends Percentile {

	public Median() {
		super(0.5);
	}

	@Override
	public String toString() {
		return "Median";
	}

}
