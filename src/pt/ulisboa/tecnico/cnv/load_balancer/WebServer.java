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

	public static LoadBalancer lb;

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
		server.createContext("/sudoku", new WebServerSudokuHandler());
		// health check endpoint
		server.createContext("/status", new WebServerStatusHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		Log.i(server.getAddress().toString());
	}

}
