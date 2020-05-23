package pt.ulisboa.tecnico.cnv.load_balancer.predictor;

import pt.ulisboa.tecnico.cnv.load_balancer.configuration.PredictorConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.request.QueryParameters;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;

/**
 * Very simple class that learns using a stochastic gradient descent
 * in 3 dimensions (2 weights)
 */
public class StochasticGradientDescent3D {

	private final double learningRate;

	private double w1;
	private double w2;
	private double bias;

	public StochasticGradientDescent3D(PredictorConfig config) {
		this.w1 = config.getWeightOne();
		this.w2 = config.getWeightTwo();
		this.bias = config.getBias();
		this.learningRate = config.getLearningRate();
	}

	private static double computePartialDiffLossModel(double prediction, long y) {
		return 2 * (prediction - y);
	}

	private static int getPartialDiffModelBias() {
		return 1;
	}

	/**
	 * Feed request data and update weights and bias
	 * @param request the request to use as a dataset point
	 */
	public void feed(Request request) {
		double prediction = getPrediction(request.getQueryParameters());
		double lossDifferential = computePartialDiffLossModel(prediction, request.getCost());

		bias = bias - learningRate * lossDifferential * getPartialDiffModelBias();
		w1 = w1 - learningRate * lossDifferential *
			request.getQueryParameters().getUnassignedEntries();
		w2 = w2 - learningRate * lossDifferential *
			request.getQueryParameters().getColumns() * request.getQueryParameters().getRows();
	}

	/**
	 * Gets a prediction
	 * @param parameters the paramteres to compute the prediction for
	 * @return the predicted cost
	 */
	public double getPrediction(QueryParameters parameters) {
		return w1 * parameters.getUnassignedEntries() +
			w2 * parameters.getColumns() * parameters.getRows() +
			bias;
	}

}
