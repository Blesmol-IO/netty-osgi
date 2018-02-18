package io.blesmol.netty.provider;

import java.util.Map;

import io.blesmol.netty.api.Property;

public class ChannelHandlerConfig {

	public static final String EMPTY = "";

	protected String appName;
	protected String handleName;

	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public String getHandleName() {
		return handleName;
	}

	public void setHandleName(String handleName) {
		this.handleName = handleName;
	}

	public static ChannelHandlerConfig fromMap(Map<String, Object> map) {

		ChannelHandlerConfig result = new ChannelHandlerConfig();
		result.handleName = (String) map.getOrDefault(Property.ChannelHandler.CHANNEL_ID, EMPTY);
		result.appName = (String) map.getOrDefault(Property.APP_NAME, EMPTY);
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appName == null) ? 0 : appName.hashCode());
		result = prime * result + ((handleName == null) ? 0 : handleName.hashCode());
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
		ChannelHandlerConfig other = (ChannelHandlerConfig) obj;
		if (appName == null) {
			if (other.appName != null)
				return false;
		} else if (!appName.equals(other.appName))
			return false;
		if (handleName == null) {
			if (other.handleName != null)
				return false;
		} else if (!handleName.equals(other.handleName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return appName + "::" + handleName;
	}

}
