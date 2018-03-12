package io.blesmol.netty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class ConfigurationUtilTest {

	private class TestConfigurationUtilImpl extends ConfigurationUtilProvider {
		
	}

	@Test
	public void shouldEscape() throws InvalidSyntaxException { 
		TestConfigurationUtilImpl testConfigUtil = new TestConfigurationUtilImpl();
		Map<String, Object> map = new HashMap<>();
		
		map.put("foo.target", "(&(a=b)(c=d))");
		map.put("port", 50);
		List<String> listString = new ArrayList<>();
		listString.add("a");
		listString.add("b");
		map.put("listString", listString);
		String[] stringArray = new String[] {"c", "(*)"};
		map.put("stringArray", stringArray);
		Boolean bool = new Boolean(false);
		map.put("boolean", bool);

		String actual = testConfigUtil.createFilterFromMap("a", "b", map);
		
		// Verify
		
		// Does it pass this test?
		FrameworkUtil.createFilter(actual);

		// Does it contain the expected pairings?
		assertTrue(actual.startsWith("(&"));
		assertTrue(actual.contains("(a=b)"));
		assertTrue(actual.contains("(boolean=false)"));
		assertTrue(actual.contains("(port=50)"));
		assertTrue(actual.contains("(foo.target=\\28&\\28a=b\\29\\28c=d\\29\\29)"));
		assertTrue(actual.contains("(stringArray=c)"));
		assertTrue(actual.contains("(stringArray=\\28\\2a\\29)"));
		assertTrue(actual.contains("(listString=a)"));
		assertTrue(actual.contains("(listString=b)"));
	}
}
