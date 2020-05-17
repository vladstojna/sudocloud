package pt.ulisboa.tecnico.cnv.worker;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.worker.result.DynamoHandler;
import pt.ulisboa.tecnico.cnv.worker.result.FileHandler;
import pt.ulisboa.tecnico.cnv.worker.result.ResultHandler;

public class WebServer {

	public static void main(final String[] args) throws Exception {

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		ResultHandler dynamoHandler = new DynamoHandler("metrics-table", "us-east-1");
		ResultHandler fileHandler = new FileHandler("metrics.txt");

		// sudoku solver endpoint
		server.createContext("/sudoku", new SudokuHandler(dynamoHandler));
		server.createContext("/file", new SudokuHandler(fileHandler));

		// health check endpoint
		server.createContext("/status", new StatusHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());

		server.start();

		System.out.println(server.getAddress().toString());
	}

}
