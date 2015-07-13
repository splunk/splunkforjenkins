package jenkins.plugins.splunkins.SplunkLogging;

import java.util.HashMap;



public class HttpInput_JavaLogging {

    public static void main(String[] args) throws Exception {

        String httpinputName = "httpInputs";
        String token = SplunkConnector.createHttpinput(httpinputName);

        String loggerName = "splunkLogger";
        HashMap<String, String> userInputs = new HashMap<String, String>();
        userInputs.put("user_httpinput_token", token);
        userInputs.put("user_logger_name", loggerName);
        LoggingConfigurations.loadJavaLoggingConfiguration("logging_template.properties", "logging.properties", userInputs);

        java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(loggerName);
//
        XmlParser parser = new XmlParser();
        parser.xmlParser(LOGGER);
        
        //SplunkConnector.deleteHttpinput(httpinputName);
    }

}
