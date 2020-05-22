package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

class WebServerSudokuHandler implements HttpHandler {

	private final LoadBalancer lb;

	public WebServerSudokuHandler(LoadBalancer lb) {
		this.lb = lb;
	}

	public void handle(final HttpExchange t) throws IOException {

		Request request = new Request.Builder()
			.withHttpExchange(t)
			.withCallback(lb)
			.build();

		// Request loadbalancer an instance to run the request on
		WorkerInstanceHolder instance = lb.getWorkerInstance();

		request.assignToInstance(instance);
		request.process();
	}
}
