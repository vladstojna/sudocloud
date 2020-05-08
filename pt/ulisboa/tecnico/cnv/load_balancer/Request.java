package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import com.sun.net.httpserver.HttpExchange;

/**
 * Represents a request to solve a puzzle
 **/
public class Request {

    static final String LOG_TAG = Request.class.getSimpleName();

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

    private Request(RequestBuilder builder) {

	this.requestId = nextRequestId.getAndIncrement();
	this.strategy = builder.strategy;
	this.un = builder.un;
	this.n1 = builder.n1;
	this.n2 = builder.n2;
	this.puzzleName = builder.puzzleName;
	this.httpExchange = builder.httpExchange;

	Log.i("Built request with:");
	Log.i("  strategy: " + this.strategy);
	Log.i("  unassigned: " + this.un);
	Log.i("  n1: " + this.n1);
	Log.i("  n2: " + this.n2);
	Log.i("  puzzleName: " + this.puzzleName);
    }

    public void assignToInstance(WorkerInstanceHolder instance) {
	this.assignedInstance = instance;
    }

    public void process() {
	if (assignedInstance == null) {
	    Log.e(LOG_TAG, "Attempeted to proces request without assigned instance");
	    return;
	}

	String instanceAddress = assignedInstance.getSolverAddress();

	Log.i("> Forwarding query to: " + instanceAddress);

	try {
	    LoadBalancer.startedProcessing(this);
	    Util.proxyRequest(httpExchange, instanceAddress);
	    LoadBalancer.finishedProcessing(this);
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
    public static class RequestBuilder {

	// Required params
	String strategy;
	int un; // unassigned entries
	int n1; // number of colums
	int n2; // number of lines
	String puzzleName; // "i" parameter

	HttpExchange httpExchange; // HTTP exchange established by loadbalancer with client

	public RequestBuilder() {}

	/**
	 * Parses url query
	 **/
	public RequestBuilder parseQuery(String query) {
	    Log.i(LOG_TAG, "parsing query");

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

	public RequestBuilder withHttpExchange(HttpExchange t) {
	    this.httpExchange = t;
	    return this;
	}

	public Request build() {
	    // FIXME verify mandatory parameters are set
	    this.parseQuery(this.httpExchange.getRequestURI().getQuery());
	    return new Request(this);
	}
    }
}
