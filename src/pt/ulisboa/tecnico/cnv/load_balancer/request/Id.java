package pt.ulisboa.tecnico.cnv.load_balancer.request;

import java.util.concurrent.atomic.AtomicLong;

public class Id {

	private static final AtomicLong nextId = new AtomicLong();

	private final long value;

	public Id() {
		value = nextId.getAndIncrement();
	}

	public long getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (value ^ (value >>> 32));
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
		Id other = (Id) obj;
		if (value != other.value)
			return false;
		return true;
	}

}
