package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.ec2.model.Instance;

public interface InstanceScaling {

	/**
	 * Asynchronously creates an instance
	 * @param instanceManager callback
	 */
	void createInstanceAsync(InstanceManager instanceManager);

	/**
	 * Asynchronously terminates an instance
	 * @param instance
	 */
	void terminateInstanceAsync(Instance instance);

}
