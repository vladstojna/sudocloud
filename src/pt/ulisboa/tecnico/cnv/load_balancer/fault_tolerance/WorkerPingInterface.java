package pt.ulisboa.tecnico.cnv.load_balancer.fault_tolerance;

import java.util.List;

public interface WorkerPingInterface {
	List<String> getWorkerIPs();
}
