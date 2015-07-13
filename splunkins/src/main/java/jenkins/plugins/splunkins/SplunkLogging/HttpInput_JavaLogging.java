import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.json.XML;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;



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
