package pt.ulisboa.tecnico.cnv.load_balancer.exception;

public class AutoScalerException extends Exception {

	private static final long serialVersionUID = 4084308868801524988L;

	public AutoScalerException(String message) {
		super(message);
	}

	public AutoScalerException(String message, Throwable cause) {
		super(message, cause);
	}

}
