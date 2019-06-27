package biz.aQute.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import aQute.bnd.build.Run;
import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.Jar;
import aQute.lib.io.IO;

public class LauncherTest {
	File						base					= new File("").getAbsoluteFile();

	private static final String	GENERATED_PACKAGED_JAR	= "generated/packaged.jar";

	private Properties			prior;

	@Before
	public void before() {
		prior = new Properties();
		prior.putAll(System.getProperties());
	}

	/**
	 * Testing the embedded launcher is quite tricky. This test uses a
	 * prefabricated packaged jar. Notice that you need to reexport that jar for
	 * every change in the launcher since it embeds the launcher. This jar is
	 * run twice to see if the second run will not reinstall the bundles.
	 */

	@After
	public void after() {
		System.setProperties(prior);
	}

	@Test
	public void testRunOrder_0_no_start_levels() throws Exception {
		File file = buildPackage("order-00.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");

		String result = runFramework(file);

		System.out.println(result);
		assertThat(result).containsPattern("startlevel: not handled");
		assertThat(result).containsPattern("Startlevel\\s+1");
		assertThat(result).containsPattern("0\\s+ACTIV\\s+<>\\s+System Bundle");
		assertThat(result).containsPattern("1\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.log");
		assertThat(result).containsPattern("1\\s+ACTIV\\s+<>\\s+jar/.?demo.jar");
		assertThat(result).containsPattern("1\\s+ACTIV\\s+<>\\s+jar/.?org.apache.servicemix.bundles.junit");
		assertThat(result).containsPattern("1\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.configadmin");
	}

	@Test
	public void testRunOrder_1_basic() throws Exception {
		File file = buildPackage("order-01.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");

		String result = runFramework(file);

		System.out.println(result);
		assertThat(result).containsPattern("handled \\[-1, 10, 20, -1, 5\\]");
		assertThat(result).containsPattern("Startlevel\\s+22");
		assertThat(result).containsPattern("0\\s+ACTIV\\s+<>\\s+System Bundle");
		assertThat(result).containsPattern("21\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.log");
		assertThat(result).containsPattern("10\\s+ACTIV\\s+<>\\s+jar/.?demo.jar");
		assertThat(result).containsPattern("20\\s+ACTIV\\s+<>\\s+jar/.?org.apache.servicemix.bundles.junit");
		assertThat(result).containsPattern("5\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.configadmin");
		assertThat(result).containsPattern("startlevel: default=21, beginning=22, now moving to 1");
		assertThat(result).containsPattern("startlevel: beginning level 22");
		assertThat(result).containsPattern("startlevel: notified reached final level 22");
	}

	@Test
	public void testRunOrder_2_decorations() throws Exception {
		File file = buildPackage("order-02.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");

		String result = runFramework(file);

		System.out.println(result);
		assertThat(result).containsPattern("Startlevel\\s+23");
		assertThat(result).containsPattern("0\\s+ACTIV\\s+<>\\s+System Bundle");
		assertThat(result).containsPattern("22\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.log");
		assertThat(result).containsPattern("11\\s+ACTIV\\s+<>\\s+jar/.?demo.jar");
		assertThat(result).containsPattern("21\\s+ACTIV\\s+<>\\s+jar/.?org.apache.servicemix.bundles.junit");
		assertThat(result).containsPattern("6\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.configadmin");
		assertThat(result).containsPattern("startlevel: default=22, beginning=23, now moving to 1");
		assertThat(result).containsPattern("startlevel: beginning level 23");
		assertThat(result).containsPattern("startlevel: notified reached final level 23");
	}

	@Test
	public void testRunOrder_3_manual_beginning_level() throws Exception {
		System.getProperties()
			.remove("launch.properties");
		File file = buildPackage("order-03.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");

		String result = runFramework(file);

		System.out.println(result);
		assertThat(result).containsPattern("Startlevel\\s+12");
		assertThat(result).containsPattern("0\\s+ACTIV\\s+<>\\s+System Bundle");
		assertThat(result).containsPattern("22\\s+RSLVD\\s+<>\\s+jar/.?org.apache.felix.log");
		assertThat(result).containsPattern("11\\s+ACTIV\\s+<>\\s+jar/.?demo.jar");
		assertThat(result).containsPattern("21\\s+RSLVD\\s+<>\\s+jar/.?org.apache.servicemix.bundles.junit");
		assertThat(result).containsPattern("6\\s+ACTIV\\s+<>\\s+jar/.?org.apache.felix.configadmin");

		assertThat(result).containsPattern("startlevel: default=22, beginning=12, now moving to 1");
		assertThat(result).containsPattern("startlevel: beginning level 12");
		assertThat(result).containsPattern("startlevel: notified reached final level 12");
	}

	@Test
	public void testPackaged() throws Exception {
		File file = buildPackage("keep.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");
		File fwdir = IO.getFile(base, "generated/keepfw");
		IO.delete(fwdir);

		assertTrue(file.isFile());

		String result = runFramework(file);
		assertTrue(result.contains("installing jar/demo.jar"));

		result = runFramework(file);
		assertTrue(result.contains("not updating jar/demo.jar because identical digest"));
	}

	/**
	 * Tests the EmbeddedLauncher by creating an instance and calling the run
	 * method. We Check if the expected exit value is printed in the result.
	 *
	 * @throws Exception
	 */
	@Test
	public void testEmbeddedLauncherWithRunMethod() throws Exception {
		File file = buildPackage("keep.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");
		File fwdir = IO.getFile(base, "generated/keepfw");
		IO.delete(fwdir);

		assertTrue(file.isFile());

		String result = runFrameworkWithRunMethod(file);
		assertTrue(result.contains("installing jar/demo.jar"));
		assertTrue(result.contains("Exited with 197"));

	}

	/**
	 * Tests the EmbeddedLauncher without any trace logging
	 *
	 * @throws Exception
	 */
	@Test
	public void testEmbeddedLauncherNoTrace() throws Exception {
		File file = buildPackage("keep_notrace.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");
		File fwdir = IO.getFile(base, "generated/keepfw");
		IO.delete(fwdir);

		assertTrue(file.isFile());

		System.setProperty("launch.trace", "false");

		String result = runFrameworkWithRunMethod(file);
		assertTrue(result.contains("quit.no.exit"));

		assertFalse(result.contains("[EmbeddedLauncher] looking for META-INF/MANIFEST.MF"));
	}

	/**
	 * Tests the EmbeddedLauncher without trace logging
	 *
	 * @throws Exception
	 */
	@Test
	public void testEmbeddedLauncherTrace() throws Exception {
		File file = buildPackage("keep_notrace.bndrun");

		System.setProperty("test.cmd", "quit.no.exit");
		File fwdir = IO.getFile(base, "generated/keepfw");
		IO.delete(fwdir);

		assertTrue(file.isFile());

		System.setProperty("launch.trace", "true");

		String result = runFrameworkWithRunMethod(file);
		assertTrue(result.contains("installing jar/demo.jar"));

		assertTrue(result.contains("[EmbeddedLauncher] looking for META-INF/MANIFEST.MF"));
	}

	private File buildPackage(String bndrun) throws Exception, IOException {
		Workspace ws = Workspace.getWorkspace(base.getParentFile());
		Run run = Run.createRun(ws, IO.getFile(base, bndrun));

		File file = IO.getFile(base, GENERATED_PACKAGED_JAR);
		try (Jar pack = run.pack(null)) {
			assertTrue(ws.check());
			assertTrue(run.check());
			pack.write(file);
		}
		return file;
	}

	private String runFramework(File file) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
		InvocationTargetException, IOException, MalformedURLException {
		PrintStream out = System.err;
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PrintStream out2 = new PrintStream(bout);
		System.setErr(out2);
		try {
			try (URLClassLoader l = new URLClassLoader(new URL[] {
				file.toURI()
					.toURL()
			}, null)) {
				Class<?> launcher = l.loadClass("aQute.launcher.pre.EmbeddedLauncher");
				Method main = launcher.getDeclaredMethod("main", String[].class);
				main.invoke(null, (Object) new String[] {});
			}

			out2.flush();
		} finally {
			System.setErr(out);
		}

		return new String(bout.toByteArray(), StandardCharsets.UTF_8);
	}

	private String runFrameworkWithRunMethod(File file)
		throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
		IOException, MalformedURLException, InstantiationException, IllegalArgumentException, SecurityException {
		PrintStream err = System.err;
		PrintStream out = System.out;
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PrintStream out2 = new PrintStream(bout);
		System.setErr(out2);
		System.setOut(out2);
		try {
			try (URLClassLoader l = new URLClassLoader(new URL[] {
				file.toURI()
					.toURL()
			}, null)) {
				Class<?> launcher = l.loadClass("aQute.launcher.pre.EmbeddedLauncher");
				Object o = launcher.getConstructor()
					.newInstance();
				Method run = launcher.getDeclaredMethod("run", String[].class);
				int result = (int) run.invoke(o, (Object) new String[] {});
				System.out.println("Exited with " + result);
			}
			out2.flush();
		} finally {
			System.setErr(err);
			System.setOut(out);
		}

		return new String(bout.toByteArray(), StandardCharsets.UTF_8);
	}
}
