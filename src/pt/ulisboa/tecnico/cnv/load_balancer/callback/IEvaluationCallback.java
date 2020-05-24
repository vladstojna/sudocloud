package pt.ulisboa.tecnico.cnv.load_balancer.callback;

import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;

public interface IEvaluationCallback {
	void createInstance(WorkerInstanceHolder createdInstance);
	void terminateInstance(WorkerInstanceHolder terminatedInstance);
}
