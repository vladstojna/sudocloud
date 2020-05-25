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

	final Thread thread = Thread.currentThread();

	public SudokuHandler(LoadBalancer lb, AutoScaler as) {
		this.lb = lb;
		this.as = as;
	}

	public void handle(final HttpExchange t) {

		final String query = t.getRequestURI().getQuery();

		Log.i(LOG_TAG, "> Query: " + query);

		try {

			QueryParameters queryParams = new QueryParameters(query);
			Request request = new Request(query, queryParams);
			request.registerHandler(new HandlerInterface() {
				// FIXME IOEXception should have been
				// thrown back to main but because it
				// is anon class it gets sent to Request
				public void onRequestFailed() {
					Log.i(LOG_TAG, "killing handler thread");
					thread.interrupt();
					handle(t);
				}
			});
			WorkerInstanceHolder instanceHolder = lb.chooseInstance(request, as);

			try {
				HttpUtil.proxyRequest(t,
						      instanceHolder.getInstance().getPublicIpAddress(),
						      lb.getWorkerInstanceConfig().getPort());

				lb.removeRequest(instanceHolder, request, as);
			} catch (IOException e) {
				Log.i(LOG_TAG, "Connection to worker aborted; Request considered not finshed");
				// remove request
				lb.removeRequest(instanceHolder, request, as);

				// process request failure, leading to the process recreation
				request.onRequestFailed();

			}

		} catch (InterruptedException e) {
		    Log.i(LOG_TAG, "Thread was stopped, likely because the worker is dead");
		}
	}
}
