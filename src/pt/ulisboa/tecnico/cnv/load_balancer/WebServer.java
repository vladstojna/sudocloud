package pt.ulisboa.tecnico.cnv.load_balancer;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.load_balancer.configuration.AutoScalerConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.DynamoDBConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.PredictorConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.configuration.WorkerInstanceConfig;
import pt.ulisboa.tecnico.cnv.load_balancer.handler.StatusHandler;
import pt.ulisboa.tecnico.cnv.load_balancer.handler.SudokuHandler;
import pt.ulisboa.tecnico.cnv.load_balancer.scaling.metric.Median;
import pt.ulisboa.tecnico.cnv.load_balancer.util.Log;

/**
 * LoadBalancer Webserver
 **/
public class WebServer {

	private static DynamoDBConfig getDynamoDBConfig() throws Exception {
		Properties props = new Properties();
		try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("dynamodb.properties")) {
			props.load(is);
			return new DynamoDBConfig(
				props.getProperty("tableName"),
				props.getProperty("region"),
				props.getProperty("keyName"),
				props.getProperty("valueName"),
				Long.parseLong(props.getProperty("readCapacity")),
				Long.parseLong(props.getProperty("writeCapacity")));
		}
	}

	private static WorkerInstanceConfig getWorkerInstanceConfig() throws Exception {
		Properties props = new Properties();
		try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("worker.properties")) {
			props.load(is);
			return new WorkerInstanceConfig(
				Integer.parseInt(props.getProperty("port")),
				props.getProperty("image"),
				props.getProperty("type"),
				props.getProperty("region"),
				props.getProperty("tagKey"),
				props.getProperty("tagValue"),
				props.getProperty("keyName"),
				props.getProperty("securityGroup"));
		}
	}

	private static PredictorConfig getPredictorConfig() throws Exception {
		Properties props = new Properties();
		try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("predictor.properties")) {
			props.load(is);
			return new PredictorConfig(
				Double.parseDouble(props.getProperty("weightOne")),
				Double.parseDouble(props.getProperty("weightTwo")),
				Double.parseDouble(props.getProperty("bias")),
				Double.parseDouble(props.getProperty("learningRate")));
		}
	}

	private static AutoScalerConfig getAutoScalerConfig() throws Exception {
		Properties props = new Properties();
		try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("autoscaler.properties")) {
			props.load(is);
			return new AutoScalerConfig(
				Integer.parseInt(props.getProperty("pollingPeriod")),
				Integer.parseInt(props.getProperty("minInstances")),
				Integer.parseInt(props.getProperty("maxInstances")),
				Integer.parseInt(props.getProperty("warmupPeriod")),
				Integer.parseInt(props.getProperty("minCpuUsage")),
				Integer.parseInt(props.getProperty("maxCpuUsage")),
				Integer.parseInt(props.getProperty("cloudWatchPeriod")),
				Integer.parseInt(props.getProperty("cloudWatchOffset")),
				props.getProperty("cloudWatchRegion"),
				TimeUnit.SECONDS);
		}
	}

	public static void main(final String[] args) throws Exception {

		DynamoDBConfig dynamoDBConfig = getDynamoDBConfig();
		WorkerInstanceConfig workerConfig = getWorkerInstanceConfig();
		PredictorConfig predictorConfig = getPredictorConfig();
		AutoScalerConfig autoScalerConfig = getAutoScalerConfig();

		LoadBalancer loadBalancer = new LoadBalancer(dynamoDBConfig, workerConfig, predictorConfig);
		AutoScaler autoScaler = new AutoScaler(autoScalerConfig, workerConfig, loadBalancer, new Median());

		autoScaler.initialInstanceStartup();

		// please note that iptables is redirecting traffic from port 80
		// to port 8080. This is so that this webserver can run as a
		// regular user (allowde only for ports above 1024)
		final HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

		// sudoku solver endpoint
		server.createContext("/sudoku", new SudokuHandler(loadBalancer, autoScaler));
		// health check endpoint
		server.createContext("/status", new StatusHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		autoScaler.start();

		Log.i(server.getAddress().toString());
	}

}
