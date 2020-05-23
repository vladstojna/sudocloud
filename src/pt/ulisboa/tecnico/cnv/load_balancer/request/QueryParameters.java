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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + columns;
		result = prime * result + ((puzzleName == null) ? 0 : puzzleName.hashCode());
		result = prime * result + rows;
		result = prime * result + ((solverStrategy == null) ? 0 : solverStrategy.hashCode());
		result = prime * result + unassignedEntries;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		QueryParameters other = (QueryParameters) obj;
		if (columns != other.columns)
			return false;
		if (puzzleName == null) {
			if (other.puzzleName != null)
				return false;
		} else if (!puzzleName.equals(other.puzzleName))
			return false;
		if (rows != other.rows)
			return false;
		if (solverStrategy == null) {
			if (other.solverStrategy != null)
				return false;
		} else if (!solverStrategy.equals(other.solverStrategy))
			return false;
		if (unassignedEntries != other.unassignedEntries)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[solverStrategy=" + solverStrategy +
			", puzzleName=" + puzzleName +
			", unassignedEntries=" + unassignedEntries +
			", size=" + columns + "x" + rows + "]";
	}

}
