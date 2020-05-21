package pt.ulisboa.tecnico.cnv.load_balancer;

public class GeneralForwarderRuntimeException extends Exception {

	private static final long serialVersionUID = 6302919590616640197L;

	public GeneralForwarderRuntimeException(String msg) {
		super(msg);
	}

	public GeneralForwarderRuntimeException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
