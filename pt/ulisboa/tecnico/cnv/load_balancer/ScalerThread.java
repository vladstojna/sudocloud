package pt.ulisboa.tecnico.cnv.load_balancer;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

/**
 * Autoscaler component. Runs in a background thread
 *
 *
 *
 *
 *
 **/
public class ScalerThread extends Thread {
    static final String LOG_TAG = ScalerThread.class.getSimpleName();

    public ScalerThread() {}
    public void run() {
	Log.i(LOG_TAG, "started autoscaler in background thread");
    }

}
