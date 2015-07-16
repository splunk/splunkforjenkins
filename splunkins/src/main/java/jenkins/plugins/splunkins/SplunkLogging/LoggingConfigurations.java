package jenkins.plugins.splunkins.SplunkLogging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import jenkins.plugins.splunkins.SplunkinsNotifier;

import com.splunk.ServiceArgs;


public class LoggingConfigurations {
	private final static Logger LOGGER = Logger
			.getLogger(SplunkinsNotifier.class.getName());
	/*
	 * create logging.property and force java logging manager to reload the
	 * configurations
	 */
	public static void loadJavaLoggingConfiguration(String configFileTemplate,
			String configFile, HashMap<String, String> userInputs) {

		try {
			ServiceArgs serviceArgs = SplunkConnector.getSplunkHostInfo();

			String configFilePath = updateConfigFile(configFileTemplate,
					configFile, userInputs, serviceArgs);

			FileInputStream configFileStream = new FileInputStream(
					configFilePath);

			LogManager.getLogManager().readConfiguration(configFileStream);

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			LOGGER.info(errors.toString());
		}
	}

	/**
	 * modify the config file with the generated token, and configured splunk
	 * host, read the template from configFileTemplate, and create the updated
	 * configfile to configFile
	 */
	public static String updateConfigFile(String configFileTemplate,
			String configFile, HashMap<String, String> userInputs,
			ServiceArgs serviceArgs) throws IOException {
		SplunkConnector.getSplunkHostInfo();

		String configFileDir = Jenkins.getInstance().getPluginManager().getPlugin("splunkins").baseResourceURL.getPath();
		LOGGER.info(configFileDir);
		List<String> lines = Files.readAllLines(new File(configFileDir,
				configFileTemplate).toPath(), Charset.defaultCharset());

		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).contains("%" + Constants.HOST + "%")) {
				lines.set(
						i,
						lines.get(i).replace("%" + Constants.HOST + "%",
								serviceArgs.host));
			}
			if (lines.get(i).contains("%" + Constants.PORT + "%")) {
				lines.set(
						i,
						lines.get(i).replace("%" + Constants.PORT + "%",
								serviceArgs.port.toString()));
			}

			if (lines.get(i).contains("%" + Constants.SCHEME + "%")) {
				lines.set(
						i,
						lines.get(i).replace("%" + Constants.SCHEME + "%",
								serviceArgs.scheme));
			}

			String match = FindUserInputConfiguration(lines.get(i));
			if (!match.isEmpty()) {
				if (userInputs.keySet().contains(match))
					lines.set(
							i,
							lines.get(i).replace("%" + match + "%",
									userInputs.get(match)));
				else
					lines.set(i, "");
			}
		}

		String configFilePath = new File(configFileDir, configFile).getPath();
		FileWriter fw = new FileWriter(configFilePath);
		for (String line : lines) {
			if (!line.isEmpty()) {
				fw.write(line);
				fw.write(System.getProperty("line.separator"));
			}
		}

		fw.flush();
		fw.close();

		final File file = new File(configFilePath);
		file.setReadable(true, false);
		file.setExecutable(true, false);
		file.setWritable(true, false);

		return configFilePath;
	}

	private static String FindUserInputConfiguration(String line) {
		String pattern = "%.*%";
		Pattern r = Pattern.compile(pattern);
		Matcher m = r.matcher(line);
		if (m.find()) {
			return m.group(0).substring(1, m.group(0).length() - 1);
		} else
			return "";
	}

}

