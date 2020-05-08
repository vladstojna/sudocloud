package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.ec2.model.Instance;

/**
 * Holder of amazon instance that essentiall works as a wrapper
 * 
 * This cannot extend Instance since those objects are instantiated by
 * the amazon ASK itself (FIXME maybe some design pattern would make
 * this possible)
 **/
public class WorkerInstanceHolder {

    private Instance instance;
    private static final String SOLVER_PORT = "8000";
    
    public WorkerInstanceHolder(Instance instance) {
	this.instance = instance;
    }

    public void processRequest(Request request) {
	
    }

    /**
     * Returns the "private_ip + port" to the solver
     **/
    public String getSolverAddress() {
	return String.format("%s:%s", instance.getPrivateIpAddress(), SOLVER_PORT);
    }
}
