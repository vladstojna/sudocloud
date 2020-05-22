package pt.ulisboa.tecnico.cnv.load_balancer.request;

public class QueryParameters {

	private String solverStrategy;
	private String puzzleName;
	private int unassignedEntries;
	private int rows;
	private int columns;

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
				case "s":
					solverStrategy = value;
					break;
				case "un":
					unassignedEntries = Integer.parseInt(value);
					break;
				case "n1":
					rows = Integer.parseInt(value);
					break;
				case "n2":
					columns = Integer.parseInt(value);
					break;
				case "i":
					puzzleName = value;
					break;
				default:
					throw new IllegalArgumentException("Query has invalid format!");
			}
		}
	}

	public String getSolverStrategy() {
		return solverStrategy;
	}

	public String getPuzzleName() {
		return puzzleName;
	}

	public int getUnassignedEntries() {
		return unassignedEntries;
	}

	public int getRows() {
		return rows;
	}

	public int getColumns() {
		return columns;
	}

}
