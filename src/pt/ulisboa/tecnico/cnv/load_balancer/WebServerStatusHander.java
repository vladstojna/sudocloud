package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;

class WebServerStatusHandler implements HttpHandler {
	public void handle(final HttpExchange t) throws IOException {
		Log.i("> Health Check");

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
