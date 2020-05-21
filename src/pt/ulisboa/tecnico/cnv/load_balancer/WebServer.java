package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.URL;
import java.net.HttpURLConnection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.Scanner;
import java.util.Map;
import java.util.List;

import com.amazonaws.services.ec2.model.Instance;

/**
 * LoadBalancer Webserver
 **/
public class WebServer {

    private static final int READ_BUFFER_SIZE = 8192;

    private static LoadBalancer lb;

    public static void main(final String[] args) throws Exception {
	// Intialize loadbalancer
	WebServer.lb = new LoadBalancer();

	// start autoscaler thread
	ScalerThread scaler = new ScalerThread(WebServer.lb);
	scaler.start();

	// please note that iptables is redirecting traffic from port 80
	// to port 8080. This is so that this webserver can run as a
	// regular user (allowde only for ports above 1024)
	final HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

	// sudoku solver endpoint
	server.createContext("/sudoku", new MyHandler());
	// health check endpoint
	server.createContext("/status", new StatusHandler());

	// be aware! infinite pool of threads!
	server.setExecutor(Executors.newCachedThreadPool());
	server.start();

	Log.i(server.getAddress().toString());
    }

    public static String parseRequestBody(InputStream is) throws IOException {
	InputStreamReader isr =  new InputStreamReader(is,"utf-8");
	BufferedReader br = new BufferedReader(isr);

	// From now on, the right way of moving from bytes to utf-8 characters:

	int b;
	StringBuilder buf = new StringBuilder(512);
	while ((b = br.read()) != -1) {
	    buf.append((char) b);

	}

	br.close();
	isr.close();

	return buf.toString();
    }

    static class StatusHandler implements HttpHandler {
	@Override
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

    static class MyHandler implements HttpHandler {

	@Override
	public void handle(final HttpExchange t) throws IOException {

	    Request request = new Request.Builder()
	                                 .withHttpExchange(t)
	                                 .withCallback(WebServer.lb)
	                                 .build();

	    // Request loadbalancer an instance to run the request on
	    WorkerInstanceHolder instance = WebServer.lb.getWorkerInstance();

	    request.assignToInstance(instance);
	    request.process();
	}
    }
}
