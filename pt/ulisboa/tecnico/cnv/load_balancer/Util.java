package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.HttpURLConnection;

import java.util.List;
import java.util.Map;

import java.io.*;

public class Util {

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
    public static void proxyRequest(HttpExchange t, String serverAddress) {
	HttpURLConnection connection = null;

	try {
	    URL url = new URL("http://" + serverAddress + t.getRequestURI());

	    // LoadBalancer to act as client of Worker instance;
	    // initiates a connection to said worker
	    connection = (HttpURLConnection) url.openConnection();
	    connection.setDoOutput(true); // allow sending data to connection

	    forwardRequestHeaders(t, connection);

	    // read request body and forward it to worker instance
	    forwardStream(t.getRequestBody(), connection.getOutputStream());

	    // build final response headers from headers received from worker instance
	    forwardResponseHeaders(connection, t);

	    t.sendResponseHeaders(200, connection.getContentLength());

	    // read response body from worker instance and add it to the final response
	    forwardStream(connection.getInputStream(), t.getResponseBody());

	} catch (Exception e) {
	    System.out.println(e.getMessage());
	    // fixme better harndle exception
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
    static void forwardRequestHeaders(HttpExchange t, HttpURLConnection c) throws ProtocolException {
	Headers lbRequestHeaders = t.getRequestHeaders();
	for (Map.Entry<String, List<String>> header : lbRequestHeaders.entrySet()) {
	    for (String value : header.getValue())
		c.setRequestProperty(header.getKey(), value);
	}
    }

    /**
     * Copies response headers from worker instance's response to the loadbalancer's
     * response.
     **/
    static void forwardResponseHeaders(HttpURLConnection c, HttpExchange t) {
	// FIXME possible information leakage: response header fields
	// include metadata about the worker instance that doest not
	// need to be leaked outside of the loadbalancer. Ignoring it
	// for now as it is non-critical.

	Map<String, List<String>> workerResponseHeaders = c.getHeaderFields();
	Headers lbResponseHeaders = t.getResponseHeaders();
	for (Map.Entry<String, List<String>> header : workerResponseHeaders.entrySet()) {
	    if (header.getKey() == null) continue; // Ignore the [HTTP/1.1 200 OK] from previous request
	    for (String value : header.getValue())
		lbResponseHeaders.add(header.getKey(), value);
	}
    }

    /**
     * Redirects data from one input stream into an outputstream
     *
     * In java9 inputStream.transferTo(outputStream) would have been a
     * nicer solution. But unfortunately this is java7
     **/
    private static void forwardStream(InputStream in, OutputStream out) throws IOException {

	byte[] buffer = new byte[READ_BUFFER_SIZE];
	InputStream mInputStream = null;
	OutputStream mOutputStream = null;

	try {
	    mInputStream = new DataInputStream(in);
	    mOutputStream = new DataOutputStream(out);

	    while (true) {
		int bytesRead = mInputStream.read(buffer);
		if (bytesRead == -1)
		    break; // End of stream is reached

		mOutputStream.write(buffer, 0, bytesRead);
	    }
	} catch (IOException e) {
	    System.out.println(e.getMessage());
	    // FIXME better handle exception
	} finally {
	    mInputStream.close();
	    mOutputStream.close();
	}
    }
}
