package pt.ulisboa.tecnico.cnv.worker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class HeartbeatHandler implements HttpHandler {

	private static final String LOG_TAG = HeartbeatHandler.class.getSimpleName();

	public HeartbeatHandler() {}

	public void handle(final HttpExchange t) throws IOException {
		final String query = t.getRequestURI().getQuery();

		// send response
		t.sendResponseHeaders(200, "OK".length());

		final OutputStream os = t.getResponseBody();
		OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
		osw.write("OK");
		osw.flush();
		osw.close();

		os.close();
	}

}
