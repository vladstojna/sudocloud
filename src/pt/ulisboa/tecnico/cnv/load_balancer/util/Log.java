package pt.ulisboa.tecnico.cnv.load_balancer.util;

/**
 * Very simple logging class
 **/
public class Log {

	public static void i(String text) {
		System.out.println(String.format("[ info ] %s", text));
	}

	public static void i(String tag, String text) {
		System.out.println(String.format("[ info ] [ %s ] %s", tag, text));
	}

	public static void e(String text) {
		System.out.println(String.format("[ erro ] %s", text));
	}

	public static void e(String tag, String text) {
		System.out.println(String.format("[ erro ] [ %s ] %s", tag, text));
	}

}
