package pt.ulisboa.tecnico.cnv.load_balancer.callback;

import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;

public class EvaluationCallback implements IEvaluationCallback {
	
	private LoadBalancer loadBalancer;
	
	public EvaluationCallback(LoadBalancer loadBalancer) {
		this.loadBalancer = loadBalancer;
	}
	
	@Override
	public void createInstance(WorkerInstanceHolder createdInstance) {
		// this.loadBalancer.addWorkerInstance(createdInstance);
	}
	
	@Override
	public void terminateInstance(WorkerInstanceHolder terminatedInstance) {
		// this.loadBalancer.markToTerminateInstance(terminateInstance)
	}
}
