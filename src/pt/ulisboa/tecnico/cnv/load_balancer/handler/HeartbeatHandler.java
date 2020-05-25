package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

import pt.ulisboa.tecnico.cnv.load_balancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.load_balancer.instance.WorkerInstanceHolder;
import pt.ulisboa.tecnico.cnv.load_balancer.request.QueryParameters;
import pt.ulisboa.tecnico.cnv.load_balancer.request.Request;
import pt.ulisboa.tecnico.cnv.load_balancer.util.HttpUtil;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;




public class HeartbeatHandler implements HttpHandler {

	private static final String LOG_TAG = HeartbeatHandler.class.getSimpleName();

	private final LoadBalancer lb;

	// interface to be implemented by loadbalancer
	public interface Callback {
		void onWorkerHeartbeat(String workerId);
	}

	public HeartbeatHandler(LoadBalancer lb) {
		this.lb = lb;
	}

	public void handle(final HttpExchange t) throws IOException {
		final String query = t.getRequestURI().getQuery();
		QueryParameters queryParams = new QueryParameters(query);

		final String workerId = queryParams.getWorkerId();
		lb.onWorkerHeartbeat(workerId);

		// send response
		t.sendResponseHeaders(200, "OK".length());

		final OutputStream os = t.getResponseBody();
		OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
		osw.write("OK");
		osw.flush();
		osw.close();

		os.close();
	}

	static class QueryParameters {
		private String workerId;

		public QueryParameters(String query) {
			parseQuery(query);
		}

		private void parseQuery(String query) {
			final String[] params = query.split("&");
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				final String key = splitParam[0];
				final String value = splitParam[1];

				switch(key) {
				case "workerId":
					workerId = value;
					break;
				case "default":
					Log.i(LOG_TAG, "Unrecognized parameter");
				}
			}
		}

		public String getWorkerId() {
			return workerId;
		}
	}

}
