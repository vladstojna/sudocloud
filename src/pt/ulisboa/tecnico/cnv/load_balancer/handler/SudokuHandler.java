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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.*;

public class SudokuHandler implements HttpHandler {

	private static final String LOG_TAG = SudokuHandler.class.getSimpleName();

	private final LoadBalancer lb;
	private final AutoScaler as;

	public InputStream requestBody;

	public interface RequestBodyReturn {
		public InputStream execute(InputStream is) throws IOException;
	}

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
						      new RequestBodyReturn() {
							      public InputStream execute(InputStream in) throws IOException {
								      // copy http exchange body for preservation
								      ByteArrayOutputStream baos = new ByteArrayOutputStream();
								      try {
									      HttpUtil.forwardStream(in, baos);
								      } catch (IOException e) {
									      HttpUtil.forwardStream(requestBody, baos);
								      }
								      InputStream tempRequestBody = new ByteArrayInputStream(baos.toByteArray()); // copy array
								      requestBody = new ByteArrayInputStream(baos.toByteArray()); // store copy for future use
								      return tempRequestBody;
							      }
						      },
						      instanceHolder.getInstance().getPrivateIpAddress(),
						      lb.getWorkerInstanceConfig().getPort());

				lb.removeRequest(instanceHolder, request, as);
			} catch (IOException e) {
				Log.i(LOG_TAG, "Connection to worker aborted; Request considered not finshed");
				e.printStackTrace();
				// remove request

				lb.removeRequest(instanceHolder, request, as);
				lb.markForRemoval(instanceHolder, as);

				// process request failure, leading to the process recreation
				request.onRequestFailed();

			}

		} catch (InterruptedException e) {
		    Log.i(LOG_TAG, "Thread was stopped, likely because the worker is dead");
		}
	}
}
