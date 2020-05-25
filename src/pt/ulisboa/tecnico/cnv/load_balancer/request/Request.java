package pt.ulisboa.tecnico.cnv.load_balancer.request;

public class Request {

	private final Id id;
	private final String query;
	private final QueryParameters queryParameters;

	private long cost;

	public Request(String query, QueryParameters queryParameters) {
		this.id = new Id();
		this.query = query;
		this.queryParameters = queryParameters;
		this.cost = 0;
	}

	public Id getId() {
		return id;
	}

	public String getQuery() {
		return query;
	}

	public QueryParameters getQueryParameters() {
		return queryParameters;
	}

	public long getCost() {
		return cost;
	}

	public void setCost(long cost) {
		this.cost = cost;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (cost ^ (cost >>> 32));
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((query == null) ? 0 : query.hashCode());
		result = prime * result + ((queryParameters == null) ? 0 : queryParameters.hashCode());
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
		Request other = (Request) obj;
		if (cost != other.cost)
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (query == null) {
			if (other.query != null)
				return false;
		} else if (!query.equals(other.query))
			return false;
		if (queryParameters == null) {
			if (other.queryParameters != null)
				return false;
		} else if (!queryParameters.equals(other.queryParameters))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[id=" + id +
			", query=" + query +
			", cost=" + cost + "]";
	}

	// Fixme do through handler interface
	public void onRequestFailed() {
		System.out.println("Failed request");
	}
}
