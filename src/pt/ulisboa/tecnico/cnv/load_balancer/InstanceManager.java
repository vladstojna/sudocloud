package pt.ulisboa.tecnico.cnv.load_balancer;

import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;

public interface InstanceManager {

	Iterable<WorkerInstanceHolder> getInstances();

	void addInstance(WorkerInstanceHolder instance);

	void markForRemoval(WorkerInstanceHolder instance);

}
