package pt.ulisboa.tecnico.cnv.worker;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileHandler implements ResultHandler {

	private final Object instanceLock = new Object();

	private final String filename;

	public FileHandler(String filename) {
		this.filename = filename;
	}

	@Override
	public void handle(MetricsResult data) {
		try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)))) {
			synchronized(instanceLock) {
				out.printf("Query: %s%nCost: %d%n", data.getKey(), data.getCost());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
