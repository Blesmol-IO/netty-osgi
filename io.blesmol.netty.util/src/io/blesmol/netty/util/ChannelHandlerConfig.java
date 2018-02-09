package io.blesmol.netty.util;

import java.util.Map;

import io.blesmol.netty.api.Property;

public class ChannelHandlerConfig {

	public static final String EMPTY = "";
	
	protected String appName;
	protected String handleName;
	protected String before;
	protected String after;
	protected Boolean first;
	protected Boolean last;

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

	public String getBefore() {
		return before;
	}

	public void setBefore(String before) {
		this.before = before;
	}

	public String getAfter() {
		return after;
	}

	public void setAfter(String after) {
		this.after = after;
	}

	public Boolean getFirst() {
		return first;
	}

	public void setFirst(Boolean first) {
		this.first = first;
	}

	public Boolean getLast() {
		return last;
	}

	public void setLast(Boolean last) {
		this.last = last;
	}

	public static ChannelHandlerConfig fromMap(Map<String, Object> map) {

		ChannelHandlerConfig result = new ChannelHandlerConfig();
		result.handleName = (String) map.getOrDefault(Property.ChannelHandler.HANDLE_NAME, EMPTY);
		result.appName = (String) map.getOrDefault(Property.APP_NAME, EMPTY);
		result.before = (String) map.getOrDefault(Property.ChannelHandler.BEFORE, EMPTY);
		result.after = (String) map.getOrDefault(Property.ChannelHandler.AFTER, EMPTY);
		result.first = (Boolean) map.getOrDefault(Property.ChannelHandler.FIRST, false);
		result.last = (Boolean) map.getOrDefault(Property.ChannelHandler.LAST, false);
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
