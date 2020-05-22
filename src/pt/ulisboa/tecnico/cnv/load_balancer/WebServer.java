package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * LoadBalancer Webserver
 **/
public class WebServer {

	public static void main(final String[] args) throws Exception {
		// Intialize loadbalancer
		LoadBalancer lb = new LoadBalancer("metrics-table", "us-east-1");

		// start autoscaler thread
		// ScalerThread scaler = new ScalerThread(lb);
		// scaler.start();

		// please note that iptables is redirecting traffic from port 80
		// to port 8080. This is so that this webserver can run as a
		// regular user (allowde only for ports above 1024)
		final HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

		// sudoku solver endpoint
		server.createContext("/sudoku", new WebServerSudokuHandler(lb));
		// health check endpoint
		server.createContext("/status", new WebServerStatusHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		Log.i(server.getAddress().toString());
	}

}
