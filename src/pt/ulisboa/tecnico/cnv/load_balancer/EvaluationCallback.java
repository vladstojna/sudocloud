package pt.ulisboa.tecnico.cnv.load_balancer;

public class EvaluationCallback implements IEvaluationCallback {
	
	private LoadBalancer loadBalancer;
	
	public EvaluationCallback(LoadBalancer loadBalancer) {
		this.loadBalancer = loadBalancer;
	}
	
	@Override
	public void createInstance(WorkerInstanceHolder createdInstance) {
		this.loadBalancer.addInstance(createdInstance);
	}
	
	@Override
	public void terminateInstance(WorkerInstanceHolder terminatedInstance) {
		// this.loadBalancer.markToTerminateInstance(terminateInstance)
	}
}
