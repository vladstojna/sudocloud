package pt.ulisboa.tecnico.cnv.load_balancer;

public interface EvaluationCallback {
	void createInstance();
	void terminateInstance(WorkerInstanceHolder worker);
}
