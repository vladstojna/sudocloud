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

}
