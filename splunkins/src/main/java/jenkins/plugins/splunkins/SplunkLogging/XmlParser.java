package jenkins.plugins.splunkins.SplunkLogging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.json.simple.parser.ParseException;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

public class XmlParser {
	private ArrayList<JSONObject> jsonObjects = new ArrayList<JSONObject>();
    private final static Logger LOGGER = Logger.getLogger(XmlParser.class.getName());

	public void xmlParser(Logger logger, String logs) {
		Object xmlJSONObj;

		try {
				if (validateXMLSchema(Constants.xsdPath, logs)){
					LOGGER.info(logs);
					xmlJSONObj = (JSONObject) XML.toJSONObject(logs);
				}
				else {
					xmlJSONObj = (String) logs;
				}
				if (xmlJSONObj instanceof JSONObject) {
					ArrayList<JSONObject> jsonObjs = parse((JSONObject) xmlJSONObj);

					for (JSONObject jsonObj : jsonObjs) logger.info(jsonObj.toString());
				} else {
					logger.info(xmlJSONObj.toString());
				}

		} catch (JSONException | ParseException e) {
			e.printStackTrace();
		} 
	}

	private ArrayList<JSONObject> parse(JSONObject json) throws JSONException,
			ParseException {

		Iterator<String> keys = json.keys();
		JSONObject commonElements = new JSONObject();

		while (keys.hasNext()) {
			String key = keys.next();
			try {
				JSONObject originalJSON = json.getJSONObject(key);
				parse(originalJSON);
				commonElements = originalJSON;
			} catch (JSONException e) {
				if (Constants.TESTCASE.equalsIgnoreCase(key)) {
					JSONArray jsonarr = json.getJSONArray(key);
					for (int n = 0; n < jsonarr.length(); n++) {
						JSONObject object = jsonarr.getJSONObject(n);

						JSONObject jsonFinal = new JSONObject();
						jsonFinal.put(key, object);

						jsonObjects.add(jsonFinal);

					}

				}
				

			}
		}
		commonElements.remove(Constants.TESTCASE);
		return merge(commonElements, jsonObjects);
	}

	private ArrayList<JSONObject> merge(JSONObject jsonObj1, ArrayList<JSONObject> jsonObjList) throws JSONException {
		ArrayList<JSONObject> arr = new ArrayList<JSONObject>();

		for (JSONObject jsonObj2 : jsonObjList) {
			JSONObject json = new JSONObject();
			json.append(Constants.TESTSUITE, jsonObj1);
			json.append(Constants.TESTSUITE, jsonObj2);
			arr.add(json);

		}

		return arr;

	}
	
	private boolean validateXMLSchema(String xsdPath, String xmlString){
        
        try {
            SchemaFactory factory = SchemaFactory.newInstance(Constants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlString)));
        } catch (IOException | SAXException e) {
        	e.printStackTrace();
            return false;
        }
        return true;
    }
}

