package pt.ulisboa.tecnico.cnv.load_balancer.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;
import pt.ulisboa.tecnico.cnv.load_balancer.handler.SudokuHandler.RequestBodyReturn;

public class HttpUtil {

	private static final String LOG_TAG = HttpUtil.class.getSimpleName();

	private static final int READ_BUFFER_SIZE = 8192;

	/**
	 * Proxies the requests from the Load Balancer to a provided
	 * worker instance and returns back the result.
	 *
	 * Note: the code is a bit spaguetti-like because the webserver
	 * processes http requests with the class HttpExchange. But there
	 * doesn't seem to be an HTTP client that works with that class
	 * (we need that to act as client when contacting the worker
	 * instance). For this reason the code is converting from
	 * HTTPExchange to HttpUrlConnection and back.
	 **/
	public static void proxyRequest(HttpExchange t, RequestBodyReturn callback , String serverAddress, int serverPort) throws IOException {

		HttpURLConnection connection = null;

		try {
			URL url = new URL("http://" + serverAddress + ":" + serverPort + t.getRequestURI());

			// LoadBalancer to act as client of Worker instance;
			// initiates a connection to said worker
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true); // allow sending data to connection

			Log.i(LOG_TAG, "Forwarding request headers");

			forwardRequestHeaders(t.getRequestHeaders(), connection);

			Log.i(LOG_TAG, "Forwarding request body");

			// read request body and forward it to worker instance
			forwardStream(callback.execute(t.getRequestBody()), connection.getOutputStream());

			// build final response headers from headers received from worker instance
			forwardResponseHeaders(t.getResponseHeaders(), connection);

			Log.i(LOG_TAG, "Forwarding back response headers");
			t.sendResponseHeaders(200, connection.getContentLengthLong());

			Log.i(LOG_TAG, "Forwarding back response body");
			// read response body from worker instance and add it to the final response
			forwardStream(connection.getInputStream(), t.getResponseBody());
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	// Functions for converting HttpExchange to HttpURLConnection

	/**
	 * Copies response headers from worker instance's response to the loadbalancer's
	 * response.
	 **/
	private static void forwardRequestHeaders(Headers requestHeaders, HttpURLConnection c) {
		for (Map.Entry<String, List<String>> header : requestHeaders.entrySet()) {
			for (String value : header.getValue()) {
				c.setRequestProperty(header.getKey(), value);
			}
		}
	}

	/**
	 * Copies response headers from worker instance's response to the loadbalancer's
	 * response.
	 **/
	private static void forwardResponseHeaders(Headers responseHeaders, HttpURLConnection c) {
		// FIXME possible information leakage: response header fields
		// include metadata about the worker instance that doest not
		// need to be leaked outside of the loadbalancer. Ignoring it
		// for now as it is non-critical.

		Map<String, List<String>> workerResponseHeaders = c.getHeaderFields();
		for (Map.Entry<String, List<String>> header : workerResponseHeaders.entrySet()) {
			if (header.getKey() == null)
				continue; // Ignore the [HTTP/1.1 200 OK] from previous request
			for (String value : header.getValue()) {
				responseHeaders.add(header.getKey(), value);
			}
		}
	}

	/**
	 * Redirects data from one input stream into an outputstream
	 *
	 * In java9 inputStream.transferTo(outputStream) would have been a
	 * nicer solution. But unfortunately this is java7
	 **/
	public static void forwardStream(InputStream in, OutputStream out) throws IOException {
		try (InputStream is = new DataInputStream(in);
			OutputStream os = new DataOutputStream(out)) {

			byte[] buffer = new byte[READ_BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}
		}
	}

}
