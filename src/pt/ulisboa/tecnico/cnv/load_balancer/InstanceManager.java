package pt.ulisboa.tecnico.cnv.load_balancer;

import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;

public interface InstanceManager {

	Iterable<WorkerInstanceHolder> getInstances();

	void addInstance(WorkerInstanceHolder holder);

	void markForRemoval(WorkerInstanceHolder holder, InstanceScaling callback) throws InterruptedException;

}
