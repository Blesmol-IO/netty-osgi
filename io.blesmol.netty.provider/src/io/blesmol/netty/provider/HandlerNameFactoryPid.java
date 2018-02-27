package io.blesmol.netty.provider;

public class HandlerNameFactoryPid {

	final String handlerName;
	final String factoryPid;

	HandlerNameFactoryPid(String handlerName, String factoryPid) {
		super();
		this.handlerName = handlerName;
		this.factoryPid = factoryPid;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((factoryPid == null) ? 0 : factoryPid.hashCode());
		result = prime * result + ((handlerName == null) ? 0 : handlerName.hashCode());
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
		HandlerNameFactoryPid other = (HandlerNameFactoryPid) obj;
		if (factoryPid == null) {
			if (other.factoryPid != null)
				return false;
		} else if (!factoryPid.equals(other.factoryPid))
			return false;
		if (handlerName == null) {
			if (other.handlerName != null)
				return false;
		} else if (!handlerName.equals(other.handlerName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getName() + " [handlerName=" + handlerName + ", factoryPid=" + factoryPid + "]";
	}
}
