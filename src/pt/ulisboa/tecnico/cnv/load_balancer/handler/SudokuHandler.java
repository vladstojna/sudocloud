package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

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

	public SudokuHandler(LoadBalancer lb) {
		this.lb = lb;
	}

	public void handle(final HttpExchange t) throws IOException {

		final String query = t.getRequestURI().getQuery();

		Log.i(LOG_TAG, "> Query: " + query);

		try {

			QueryParameters queryParams = new QueryParameters(query);
			Request request = new Request(query, queryParams);

			lb.enqueueRequest(request);
			WorkerInstanceHolder instanceHolder = lb.chooseInstance();

			HttpUtil.proxyRequest(t, instanceHolder.getInstance().getPublicIpAddress(),
				lb.getWorkerInstanceConfig().getPort());

			lb.removeRequest(instanceHolder, request);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
