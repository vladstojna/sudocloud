package pt.ulisboa.tecnico.cnv.load_balancer;

public interface IEvaluationCallback {
	void createInstance(WorkerInstanceHolder createdInstance);
	void terminateInstance(WorkerInstanceHolder terminatedInstance);
}
