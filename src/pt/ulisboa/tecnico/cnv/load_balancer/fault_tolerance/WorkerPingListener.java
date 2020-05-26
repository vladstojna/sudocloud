package pt.ulisboa.tecnico.cnv.load_balancer.fault_tolerance;

import java.util.Iterator;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.AutoScaler;

public interface WorkerPingListener {
    void onInstanceUnreachable(WorkerInstanceHolder instance, AutoScaler as);
}
