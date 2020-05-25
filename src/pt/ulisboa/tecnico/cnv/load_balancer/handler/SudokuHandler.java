package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.load_balancer.AutoScaler;
import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.request.QueryParameters;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.HttpUtil;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

import java.io.IOException;

public class SudokuHandler implements HttpHandler {

	private static final String LOG_TAG = SudokuHandler.class.getSimpleName();

	private final LoadBalancer lb;
	private final AutoScaler as;

	public SudokuHandler(LoadBalancer lb, AutoScaler as) {
		this.lb = lb;
		this.as = as;
	}

	public void handle(final HttpExchange t) throws IOException {

		final String query = t.getRequestURI().getQuery();

		Log.i(LOG_TAG, "> Query: " + query);

		try {

			QueryParameters queryParams = new QueryParameters(query);
			Request request = new Request(query, queryParams);
			WorkerInstanceHolder instanceHolder = lb.chooseInstance(request);
			
			HttpUtil.proxyRequest(t,
				instanceHolder.getInstance().getPrivateIpAddress(),
				lb.getWorkerInstanceConfig().getPort());

			lb.removeRequest(instanceHolder, request, as);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
