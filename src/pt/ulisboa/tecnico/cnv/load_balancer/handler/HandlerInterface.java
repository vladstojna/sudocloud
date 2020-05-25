package pt.ulisboa.tecnico.cnv.load_balancer.handler;

import com.sun.net.httpserver.HttpHandler;
import java.lang.Thread;

public interface HandlerInterface {
	public void onRequestFailed();
}


