package pt.ulisboa.tecnico.cnv.load_balancer.exception;

public class LoadBalancerException extends Exception {

	private static final long serialVersionUID = -2165771042773229591L;

	public LoadBalancerException(String message) {
		super(message);
	}

	public LoadBalancerException(String message, Throwable cause) {
		super(message, cause);
	}

}
