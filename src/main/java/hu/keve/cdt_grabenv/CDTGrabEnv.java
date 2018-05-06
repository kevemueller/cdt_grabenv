/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org>
 * 
 * Initial version written 2018 by Keve Müller and published to 
 * <https://github.com/kevemueller/cdt_grabenv>
 */

package hu.keve.cdt_grabenv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

/**
 * See https://github.com/kevemueller/cdt_grabenv for overall help on this hack.
 * NB: Be sure to close the project in Eclipse before running this code.
 * 
 * @author Keve Müller
 *
 */
public final class CDTGrabEnv {
	private static final String VS_COMMUNITY = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community";
	private static final Pattern ENVLINE = Pattern.compile("(.*)=(.*)");
	private static final String RE_ALL = ".+";
	private static final OptionParser optionParser = new OptionParser();

	public static void main(final String[] args)
			throws IOException, InterruptedException, XPathException, SAXException, ParserConfigurationException {
		final File vsCommunityDirectory = new File(VS_COMMUNITY);
		final File currentDirectory = new File(".");

		final ArgumentAcceptingOptionSpec<String> archOption = optionParser
				.accepts("arch",
						"Architecture to pass to vcvarsall. E.g. x64, x86, x64_[x86|arm|arm64], x86_[x64|arm|arm64]")
				.withRequiredArg().ofType(String.class).defaultsTo("x64");
		final ArgumentAcceptingOptionSpec<File> vsRootOption = optionParser
				.accepts("vsRoot", "Visual Studio root directory").withRequiredArg().ofType(File.class)
				.defaultsTo(vsCommunityDirectory);
		final ArgumentAcceptingOptionSpec<File> projectRootOption = optionParser
				.accepts("projectRoot", "Eclipse CDT project root directory").withRequiredArg().ofType(File.class)
				.defaultsTo(currentDirectory);

		final ArgumentAcceptingOptionSpec<String> namePatternOption = optionParser
				.accepts("namePattern", "CDT configuration name filter (regular expression)").withRequiredArg()
				.ofType(String.class).defaultsTo(RE_ALL);

		final OptionSpecBuilder dryRunOption = optionParser.acceptsAll(Arrays.asList("dry-run", "n"));
		final OptionSpecBuilder verboseOption = optionParser.acceptsAll(Arrays.asList("verbose", "v"));
		final AbstractOptionSpec<Void> helpOption = optionParser.acceptsAll(Arrays.asList("help", "?", "h")).forHelp();

		try {
			final OptionSet options = optionParser.parse(args);

			if (options.has(helpOption)) {
				printHelp();
				System.exit(0);
			}

			final boolean verbose = options.has(verboseOption);

			// grab shell
			String cmdExe = System.getenv("comspec");
			if (null == cmdExe) {
				cmdExe = "cmd.exe";
			}

			final Runtime runtime = Runtime.getRuntime();

			// grab the default environment
			final Map<String, String> mapBase = new LinkedHashMap<String, String>();
			grabEnvironment(mapBase, runtime, cmdExe, "/c", "\"set\"");

			// grab the VC environment
			final String arch = options.valueOf(archOption);
			final File vsRoot = options.valueOf(vsRootOption);
			final File vcVarsAllBat = new File(vsRoot, "VC\\Auxiliary\\Build\\vcvarsall.bat");
			final Map<String, String> mapVS = new LinkedHashMap<String, String>();
			final String cmdParam = "\"\"" + vcVarsAllBat + "\" " + arch + " & set\"";
			grabEnvironment(mapVS, runtime, cmdExe, "/c", cmdParam);

			// compute the delta (added/modified) by the script)
			final Collection<EnvChange> deltaEnv = deltifyEnvironment(mapBase, mapVS);
			if (verbose) {
				dump(System.out, deltaEnv);
			}

			// load the CDT Configuration names and corresponding ids
			final File projectRoot = options.valueOf(projectRootOption);
			final HashMap<String, String> idNameMap = loadIds(projectRoot);
			if (verbose) {
				System.out.println(idNameMap);
			}

			final boolean dryRun = options.has(dryRunOption);
			final Pattern namePattern = Pattern.compile(options.valueOf(namePatternOption));

			// apply the configuration changes
			for (Entry<String, String> nameId : idNameMap.entrySet()) {
				final Matcher nameMatcher = namePattern.matcher(nameId.getValue());
				if (nameMatcher.matches()) {
					if (verbose) {
						System.out
								.println("Applying environment to " + nameId.getValue() + "(" + nameId.getKey() + ")");
					}
					if (!dryRun) {
						apply(projectRoot, nameId.getKey(), deltaEnv);
					}
				}
			}

			System.exit(0);
		} catch (OptionException ex) {
			System.out.println(ex.getLocalizedMessage());
			printHelp();
			System.exit(1);
		}
	}

	private static HashMap<String, String> loadIds(final File projectRoot)
			throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
		final XPathExpression xpathExpression = XPathFactory.newInstance().newXPath()
				.compile("//storageModule[@moduleId=\"org.eclipse.cdt.core.settings\" and @name]");

		final File cProjectFile = new File(projectRoot, ".cproject");
		final Document cProjectDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cProjectFile);
		final NodeList nodes = (NodeList) xpathExpression.evaluate(cProjectDocument, XPathConstants.NODESET);
		final HashMap<String, String> idNameMap = new HashMap<String, String>();
		for (int i = 0; i < nodes.getLength(); i++) {
			NamedNodeMap nodeAttributes = nodes.item(i).getAttributes();
			final String oldName = idNameMap.put(nodeAttributes.getNamedItem("id").getNodeValue(),
					nodeAttributes.getNamedItem("name").getNodeValue());
			if (null != oldName) {
				throw new IllegalStateException("Ambiguous id " + nodeAttributes.getNamedItem("id ") + " for name "
						+ nodeAttributes.getNamedItem("name") + " and " + oldName);
			}
		}
		return idNameMap;
	}

	private static void printHelp() throws IOException {
		System.out.println("CDTGrabEnv -- Apply environment to Eclipse CDT configurations");
		System.out.println("Usage:");
		System.out.println("java -jar CDTGrabEnv.jar <<options>>");
		optionParser.printHelpOn(System.out);
	}

	private static void apply(final File projectDir, final String configurationId, final Collection<EnvChange> deltaEnv)
			throws IOException {
		Objects.requireNonNull(configurationId);

		final File settingsDir = new File(projectDir, ".settings");
		if (!settingsDir.exists()) {
			settingsDir.mkdir();
		}
		final File prefsFile = new File(settingsDir, "org.eclipse.cdt.core.prefs");
		BufferedWriter out = null;
		try {
			if (prefsFile.exists()) {
				out = new BufferedWriter(new FileWriter(prefsFile, true));
			} else {
				out = new BufferedWriter(new FileWriter(prefsFile));
				out.write("eclipse.preferences.version=1");
				out.newLine();
			}
			for (EnvChange delta : deltaEnv) {
				final String upperKey = delta.key.toUpperCase();
				final String escapedValue = delta.value.replaceAll("([:\\\\])", "\\\\$1");
				out.write("environment/project/" + configurationId + "/" + upperKey + "/delimiter=;");
				out.newLine();
				out.write("environment/project/" + configurationId + "/" + upperKey + "/operation=append");
				out.newLine();
				out.write("environment/project/" + configurationId + "/" + upperKey + "/value=" + escapedValue);
				out.newLine();
			}
		} finally {
			if (null != out) {
				out.close();
			}
		}

	}

	private static void dump(final PrintStream out, final Collection<EnvChange> deltaEnv) {
		for (final EnvChange delta : deltaEnv) {
			switch (delta.change) {
			case ADD:
				System.out.println("ADDED: " + delta.key + "=" + delta.value);
				break;
			case MODIFY:
				System.out.println("MODIFIED: " + delta.key + "=" + delta.value);
				break;
			case MODIFY_PREFIX:
				System.out.println("MODIFIED_PREFIX: " + delta.key + "+=" + delta.value);
				break;
			case MODIFY_SUFFIX:
				System.out.println("MODIFIED_SUFFIX: " + delta.key + "=+" + delta.value);
				break;
			case REMOVE:
				System.out.println("REMOVED: " + delta.key);
				break;
			default:
				throw new IllegalArgumentException(delta.change.toString());
			}
		}
	}

	private static enum Change {
		ADD, MODIFY_PREFIX, MODIFY_SUFFIX, MODIFY, REMOVE;
	}

	private static class EnvChange {
		private final Change change;
		private final String key;
		private final String value;

		private EnvChange(String key) {
			this(Change.REMOVE, key, null);
		}

		private EnvChange(Change change, String key, String value) {
			this.change = change;
			this.key = key;
			this.value = value;
		}
	}

	private static Collection<EnvChange> deltifyEnvironment(final Map<String, String> mapBase,
			final Map<String, String> mapVS) {
		final ArrayList<EnvChange> envChange = new ArrayList<EnvChange>();
		for (final Entry<String, String> vsEnv : mapVS.entrySet()) {
			final String baseValue = mapBase.get(vsEnv.getKey());
			if (null == baseValue) {
				envChange.add(new EnvChange(Change.ADD, vsEnv.getKey(), vsEnv.getValue()));
			} else {
				final String vsValue = vsEnv.getValue();
				if (!baseValue.equals(vsValue)) {
					int idx = vsValue.indexOf(baseValue);
					if (-1 == idx) {
						envChange.add(new EnvChange(Change.MODIFY, vsEnv.getKey(), vsValue));
					} else {
						if (idx > 0) {
							envChange.add(
									new EnvChange(Change.MODIFY_PREFIX, vsEnv.getKey(), vsValue.substring(0, idx)));
						}
						idx += baseValue.length();
						if (idx < vsValue.length()) {
							envChange.add(new EnvChange(Change.MODIFY_SUFFIX, vsEnv.getKey(), vsValue.substring(idx)));
						}
					}
				}
			}
		}
		mapBase.keySet().removeAll(mapVS.keySet());
		for (final String mapEnvKey : mapBase.keySet()) {
			envChange.add(new EnvChange(mapEnvKey));
		}
		return envChange;
	}
	
	private static int grabEnvironment(final Map<String, String> map, final Runtime runtime, final String... cmdArray)
			throws IOException, InterruptedException {
		final Process process = runtime.exec(cmdArray);
		try (final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while (null != (line = in.readLine())) {
				Matcher matcher = ENVLINE.matcher(line);
				if (matcher.matches()) {
					map.put(matcher.group(1), matcher.group(2));
				}
			}
		}
		return process.waitFor();
	}
}
