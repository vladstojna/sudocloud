package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.request.QueryParameters;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.HttpUtil;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

import java.io.*;

public class HeartbeatHandler implements HttpHandler {

	private static final String LOG_TAG = SudokuHandler.class.getSimpleName();

	private final LoadBalancer lb;

	public HeartbeatHandler(LoadBalancer lb) {
		this.lb = lb;
	}

	public void handle(final HttpExchange t) throws IOException {
		Log.i("> Health Check");

		final String query = t.getRequestURI().getQuery();

		Log.i(LOG_TAG, "> Query: " + query);


		// Send response to browser.
		final Headers hdrs = t.getResponseHeaders();

		hdrs.add("Content-Type", "text/html");

		hdrs.add("Access-Control-Allow-Origin", "*");

		hdrs.add("Access-Control-Allow-Credentials", "true");
		hdrs.add("Access-Control-Allow-Methods", "GET");
		hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

		t.sendResponseHeaders(200, "OK".length());

		final OutputStream os = t.getResponseBody();
		OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
		osw.write("OK");
		osw.flush();
		osw.close();

		os.close();

		Log.i("> Sent response to " + t.getRemoteAddress().toString());
	}
}
