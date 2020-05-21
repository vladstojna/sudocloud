package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import com.sun.net.httpserver.HttpExchange;

/**
 * Represents a request to solve a puzzle
 **/
public class Request {

    static String LOG_TAG = Request.class.getSimpleName();

    static AtomicInteger nextRequestId = new AtomicInteger(0);

    // Required params
    String strategy;
    int un; // unassigned entries
    int n1; // number of colums
    int n2; // number of lines
    String puzzleName; // "i" parameter

    //
    int requestId;
    WorkerInstanceHolder assignedInstance;
    HttpExchange httpExchange; // HTTP exchange established by loadbalancer with client
    LoadBalancer lb;

    private Request(Builder builder) {

	this.requestId = nextRequestId.getAndIncrement();
	LOG_TAG = String.format("%s %d", Request.class.getSimpleName(), requestId);

	this.strategy = builder.strategy;
	this.un = builder.un;
	this.n1 = builder.n1;
	this.n2 = builder.n2;
	this.puzzleName = builder.puzzleName;
	this.httpExchange = builder.httpExchange;
	this.lb = builder.lb;

	Log.i(LOG_TAG, "Request parameters" +
	               " | s: " + this.strategy +
	               " | un: " + this.un +
	               " | n1: " + this.n1 +
	               " | n2: " + this.n2 +
	               " | i: " + this.puzzleName);
   }

    public void assignToInstance(WorkerInstanceHolder instance) {
	this.assignedInstance = instance;
    }

    public void process() {
	if (assignedInstance == null) {
	    Log.e(LOG_TAG, "Attempted to proces request without assigned instance");
	    return;
	}

	String instanceAddress = assignedInstance.getSolverAddress();

	Log.i(LOG_TAG, String.format("forwarded to instance %s", assignedInstance.getId()));

	try {
	    this.lb.startedProcessing(this);
	    Util.proxyRequest(httpExchange, instanceAddress);
	    this.lb.finishedProcessing(this);
	    Log.i("> Sent response to user: " + httpExchange.getRemoteAddress().toString());
	} catch (GeneralForwarderRuntimeException e) {
	    Log.e("Request failed");
	    Log.e(e.getMessage());
	}
    }

    public int getId() {
	return requestId;
    }

    /**
     * Request builder
     *
     * Builder pattern
     **/
    public static class Builder {

	static final String LOG_TAG = "Request.Builder";

	// Required params
	String strategy;
	int un; // unassigned entries
	int n1; // number of colums
	int n2; // number of lines
	String puzzleName; // "i" parameter

	HttpExchange httpExchange; // HTTP exchange established by loadbalancer with client
	LoadBalancer lb; // callback reference

	public Builder() {}

	/**
	 * Parses url query
	 **/
	public Builder parseQuery(String query) {

	    // parsing url query
	    final String[] params = query.split("&");

	    final ArrayList<String> newArgs = new ArrayList<>();
	    for (final String p : params) {
		final String[] splitParam = p.split("=");
		String key = splitParam[0];
		String value = splitParam[1];
		//Log.i(key + " : " + value);

		switch(key) {
		case "s":
		    this.strategy = value;
		    break;
		case "un":
		    this.un = Integer.parseInt(value);
		    break;
		case "n1":
		    this.n1 =  Integer.parseInt(value);
		    break;
		case "n2":
		    this.n2 =  Integer.parseInt(value);
		    break;
		case "i":
		    this.puzzleName = value;
		    break;
		default:
		    // FIXME throw exception
		    Log.e("query with wrong params");
		    break;
		}
	    }

	    return this;
	}

	public Builder withHttpExchange(HttpExchange t) {
	    this.httpExchange = t;
	    return this;
	}

	public Builder withCallback(LoadBalancer lb) {
	    this.lb = lb;
	    return this;
	}

	public Request build() {
	    // FIXME verify mandatory parameters are set
	    this.parseQuery(this.httpExchange.getRequestURI().getQuery());
	    return new Request(this);
	}
    }
}
