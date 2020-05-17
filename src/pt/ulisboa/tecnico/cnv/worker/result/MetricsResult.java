package pt.ulisboa.tecnico.cnv.worker.result;

public class MetricsResult {

	private final String key;
	private final long cost;

	public MetricsResult(String key, long cost) {
		this.key = key;
		this.cost = cost;
	}

	public String getKey() {
		return key;
	}

	public long getCost() {
		return cost;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (cost ^ (cost >>> 32));
		result = prime * result + ((key == null) ? 0 : key.hashCode());
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
		MetricsResult other = (MetricsResult) obj;
		if (cost != other.cost)
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MetricsResult [cost=" + cost + ", key=" + key + "]";
	}

}
