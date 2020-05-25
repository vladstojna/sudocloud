package pt.ulisboa.tecnico.cnv.load_balancer.request;

import pt.ulisboa.tecnico.cnv.load_balancer.handler.HandlerInterface;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

public class Request {

	private static final String LOG_TAG = Request.class.getSimpleName();

	private final Id id;
	private final String query;
	private final QueryParameters queryParameters;

	private long cost;
	private volatile HandlerInterface handler;

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
		Log.i(LOG_TAG, "Request with id '" + getId() + " failed");
		if (handler != null)
			handler.onRequestFailed();
	}

	public void registerHandler(HandlerInterface handler) {
		this.handler = handler;
	}

	public void unregisterHander() {
		this.handler = null;
	}
}
