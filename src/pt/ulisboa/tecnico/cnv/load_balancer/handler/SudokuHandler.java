package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.request.QueryParameters;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.HttpUtil;

import java.io.IOException;

public class SudokuHandler implements HttpHandler {

	private final LoadBalancer lb;

	public SudokuHandler(LoadBalancer lb) {
		this.lb = lb;
	}

	public void handle(final HttpExchange t) throws IOException {

		final String query = t.getRequestURI().getQuery();

		QueryParameters queryParams = new QueryParameters(query);
		Request request = new Request(query, queryParams);

		WorkerInstanceHolder instanceHolder = lb.chooseInstance(request);

		HttpUtil.proxyRequest(t, instanceHolder.getInstance().getPublicIpAddress(),
			lb.getWorkerInstanceConfig().getPort());

		lb.removeRequest(instanceHolder, request);
	}
}
