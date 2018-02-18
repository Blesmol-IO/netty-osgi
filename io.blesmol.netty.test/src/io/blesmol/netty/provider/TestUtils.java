package io.blesmol.netty.provider;

import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class TestUtils {


	// TODO: don't leaks service trackers
	public static <T> T getService(BundleContext context, Class<T> clazz, long timeout) throws Exception {
		return getService(context, clazz, timeout, "");
	}

	public static <T> T getService(BundleContext context, Class<T> clazz, long timeout, String filter) throws Exception {
		ServiceTracker<T, T> st;
		if (filter != null && !"".equals(filter)) {
			st = new ServiceTracker<>(context, context.createFilter(filter), null);
		} else {
			st = new ServiceTracker<>(context, clazz, null);
		}
		st.open();
		return st.waitForService(timeout);
	}

}
