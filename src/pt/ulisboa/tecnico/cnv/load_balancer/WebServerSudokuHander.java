package pt.ulisboa.tecnico.cnv.load_balancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

class WebServerSudokuHandler implements HttpHandler {

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
