package pt.ulisboa.tecnico.cnv.load_balancer.util;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Very simple logging class
 **/
public class Log {

	private static final String format = "yyyy-MM-dd HH:mm:ss.SSS";
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(format);

	private static void log(PrintStream stream, String format, Object... args) {
		stream.printf(format, args);
	}

	public static final void i(String text) {
		log(System.out, "[%s] [%s] %s%n", dateFormat.format(new Date()), "info", text);
	}

	public static final void i(String tag, String text) {
		log(System.out, "[%s] [%s] [%s] %s%n", dateFormat.format(new Date()), "info", tag, text);
	}

	public static final void e(String text) {
		log(System.out, "[%s] [%s] %s%n", dateFormat.format(new Date()), "info", text);
	}

	public static final void e(String tag, String text) {
		log(System.out, "[%s] [%s] [%s] %s%n", dateFormat.format(new Date()), "info", tag, text);
	}

	public static final void e(String tag, Throwable t) {
		log(System.out, "[%s] [%s] [%s] %s%n", dateFormat.format(new Date()), "info", tag, t.getMessage());
		t.printStackTrace();
	}

}
