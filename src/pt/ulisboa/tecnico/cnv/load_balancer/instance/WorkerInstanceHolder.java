package pt.ulisboa.tecnico.cnv.load_balancer.instance;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.services.ec2.model.CpuOptions;
import com.amazonaws.services.ec2.model.Instance;

import pt.ulisboa.tecnico.cnv.load_balancer.request.Id;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;

/**
 * Holder that has all information regarding an instance,
 * the Instance it runs on, all the requests currently being processed by that Instance
 * and the total cost of these requests
 */
public class WorkerInstanceHolder implements Comparable<WorkerInstanceHolder> {

	private final Instance instance;
	private final Map<Id, Request> requests;
	private long totalCost;
	private long requestCapacity;
	private boolean markedForRemoval;

	/**
	 * Compares instances in regards to their current workload.
	 * Returns the most available instance in terms of load.
	 */
	public static final class BalancedComparator implements Comparator<WorkerInstanceHolder> {
		@Override
		public int compare(WorkerInstanceHolder o1, WorkerInstanceHolder o2) {

			if (o1.getRequestCapacity() > o2.getRequestCapacity())
				return -1;

			if (o1.getRequestCapacity() < o2.getRequestCapacity())
				return 1;

			if (o1.getTotalCost() < o2.getTotalCost())
				return -1;

			if (o1.getTotalCost() == o2.getTotalCost()) {
				return o1.equals(o2) == true ? 0 : 1;
			}

			return 1;
		}
	}

	public WorkerInstanceHolder(Instance instance) {
		this.instance = instance;
		requests = new ConcurrentHashMap<>();
		totalCost = 0;
		CpuOptions cpuOptions = instance.getCpuOptions();
		requestCapacity = cpuOptions.getCoreCount() * cpuOptions.getThreadsPerCore();
		markedForRemoval = false;
	}

	public boolean isAvailable() {
		return markedForRemoval == false && requestCapacity > 0;
	}

	public void markForRemoval() {
		markedForRemoval = true;
	}

	public boolean canRemove() {
		return markedForRemoval == true && totalCost == 0;
	}

	public Instance getInstance() {
		return instance;
	}

	public Request getRequest(Id id) {
		return requests.get(id);
	}

	public long getTotalCost() {
		return totalCost;
	}

	public long getRequestCapacity() {
		return requestCapacity;
	}

	public void addRequest(Request req) {
		requests.put(req.getId(), req);
		totalCost += req.getCost();
		requestCapacity--;
	}

	public void removeRequest(Id id) {
		Request req = requests.remove(id);
		if (req != null) {
			totalCost -= req.getCost();
			requestCapacity++;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((instance == null) ? 0 : instance.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WorkerInstanceHolder other = (WorkerInstanceHolder) obj;
		if (instance == null) {
			if (other.instance != null)
				return false;
		} else if (!instance.equals(other.instance))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "WorkerInstanceHolder [instance=" + instance.getInstanceId() +
			", requests=" + requests.size() +
			", totalCost=" + totalCost +
			", requestCapacity=" + requestCapacity + "]";
	}

	@Override
	public int compareTo(WorkerInstanceHolder o) {
		if (getRequestCapacity() > o.getRequestCapacity())
			return -1;

		if (getRequestCapacity() < o.getRequestCapacity())
			return 1;

		if (getTotalCost() < o.getTotalCost())
			return -1;

		if (getTotalCost() == o.getTotalCost()) {
			return equals(o) == true ? 0 : 1;
		}

		return 1;
	}

}
