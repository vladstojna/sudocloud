package pt.ulisboa.tecnico.cnv.load_balancer.configuration;

public class PredictorConfig {

	private final double weightOne;
	private final double weightTwo;
	private final double bias;
	private final double learningRate;

	public PredictorConfig(double weightOne, double weightTwo, double bias, double learningRate) {
		this.weightOne = weightOne;
		this.weightTwo = weightTwo;
		this.bias = bias;
		this.learningRate = learningRate;
	}

	public double getWeightOne() {
		return weightOne;
	}

	public double getWeightTwo() {
		return weightTwo;
	}

	public double getBias() {
		return bias;
	}

	public double getLearningRate() {
		return learningRate;
	}

}
