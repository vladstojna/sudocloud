package pt.ulisboa.tecnico.cnv.load_balancer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a request to solve a puzzle
 **/
public class Request {

    static AtomicInteger nextRequestId = new AtomicInteger(0);

    // Required params
    String strategy;
    int un; // unassigned entries
    int n1; // number of colums
    int n2; // number of lines
    String puzzleName; // "i" parameter

    //
    int requestId;

    private Request(RequestBuilder builder) {
	this.requestId = nextRequestId.getAndIncrement();
	this.strategy = builder.strategy;
	this.un = builder.un;
	this.n1 = builder.n1;
	this.n2 = builder.n2;
	this.puzzleName = builder.puzzleName;

	System.out.println("Built request with:");
	System.out.println("  strategy: " + this.strategy);
	System.out.println("  unassigned: " + this.un);
	System.out.println("  n1: " + this.n1);
	System.out.println("  n2: " + this.n2);
	System.out.println("  puzzleName: " + this.puzzleName);
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

	public RequestBuilder() {}

	/**
	 * Parses url query
	 **/
	public RequestBuilder withQuery(String query) {
	    // parsing url query
	    final String[] params = query.split("&");

	    final ArrayList<String> newArgs = new ArrayList<>();
	    for (final String p : params) {
		final String[] splitParam = p.split("=");
		String key = splitParam[0];
		String value = splitParam[1];
		//System.out.println(key + " : " + value);

		switch(key) {
		case "s":
		    this.withStrategy(value);
		    break;
		case "un":
		    this.withUnassigned(value);
		    break;
		case "n1":
		    this.withNColumns(value);
		    break;
		case "n2":
		    this.withNLines(value);
		    break;
		case "i":
		    this.withPuzzleName(value);
		    break;
		default:
		    // FIXME throw exception
		    System.out.println("query with wrong params");
		    break;
		}
	    }

	    return this;
	}

	public RequestBuilder withStrategy(String strategy) {
	    this.strategy = strategy;
	    return this;
	}

	public RequestBuilder withUnassigned(String un) {
	    this.un = Integer.parseInt(un);
	    return this;
	}

	public RequestBuilder withNColumns(String nColumns) {
	    this.n1 = Integer.parseInt(nColumns);
	    return this;
	}

	public RequestBuilder withNLines(String nLines) {
	    this.n1 = Integer.parseInt(nLines);
	    return this;
	}

	public RequestBuilder withPuzzleName(String name) {
	    this.puzzleName = name;
	    return this;
	}

	public Request build() {
	    // FIXME verify mandatory parameters are set
	    return new Request(this);
	}
    }
}
