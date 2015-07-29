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

public class XmlParser {
    private ArrayList<JSONObject> jsonObjects = new ArrayList<JSONObject>();
    private JSONObject finalJSON = new JSONObject();
    private boolean entryOnce;

    /**
     * Parses the input XML to a JSON and then massages the JSON object as per requirement.
     *
     * @param logs
     * @return
     */
    public ArrayList<JSONObject> xmlParser(String logs) {
        Object xmlJSONObj = null;
        ArrayList<JSONObject> jsonObjs = null;

        try {
            if (validateXMLSchema(Constants.xsdPath, logs)){
                xmlJSONObj = (JSONObject) XML.toJSONObject(logs);
            }
            else {
                //TODO: Add parsing for other files
            }
            if (xmlJSONObj instanceof JSONObject) {
                jsonObjs = parse((JSONObject) xmlJSONObj);
                return jsonObjs;

            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
        return jsonObjs;
    }

    /**
     * Create a custom JSON object that is needed to be send to Splunk.
     * 
     * @param json
     * @return
     * @throws JSONException
     * @throws ParseException
     */
    private ArrayList<JSONObject> parse(JSONObject json) throws JSONException,
            ParseException {

        if (!entryOnce) {
            JSONObject transformedJSON = null;
            Iterator<String> originalKeys = json.keys();
            while (originalKeys.hasNext()) {
                String key = originalKeys.next();
                transformedJSON = customJSONObject(json.getJSONObject(key));
                entryOnce = true;
            }

            finalJSON.put(Constants.TESTSUITE, transformedJSON);
        }

        Iterator<String> keys = finalJSON.keys();
        JSONObject commonElements = new JSONObject();

        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject originalJSON = finalJSON.getJSONObject(key);
                finalJSON = originalJSON;
                parse(finalJSON);
                commonElements = originalJSON;
            } catch (JSONException e) {
                if (Constants.TESTCASE.equalsIgnoreCase(key)) {
                    JSONArray jsonarr = finalJSON.getJSONArray(key);
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

    /**
     * Merges the JSON Objects to form final custom JSONObject
     * 
     * @param jsonObj1
     * @param jsonObjList
     * @return
     * @throws JSONException
     */
    public ArrayList<JSONObject> merge(JSONObject jsonObj1,
            ArrayList<JSONObject> jsonObjList) throws JSONException {
        ArrayList<JSONObject> arr = new ArrayList<>();

        for (JSONObject jsonObj2 : jsonObjList) {
            JSONObject json = new JSONObject();
            json.append(Constants.TESTSUITE, jsonObj1);
            json.append(Constants.TESTSUITE, jsonObj2);
            arr.add(json);
        }

        return arr;
    }
    
    /**
     * Validates the input XML against xsd schema
     * 
     * @param xsdPath
     * @param xmlString
     * @return
     */

    public boolean validateXMLSchema(String xsdPath, String xmlString) {
        try {
            SchemaFactory factory = SchemaFactory
                    .newInstance(Constants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlString)));
        } catch (IOException | SAXException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 
     * This method is a workaround as XML.toJSON has a behavior where it forms a JSONArray when multiple elements have same name
     * and if only 1 element exists, it doesn't create a JSONArray. 
     * Workaround is always create JSONArray (in this case for testcase tag in XML)
     *
     * @param json
     * @return
     * @throws ParseException
     * @throws JSONException
     */
    private JSONObject customJSONObject(JSONObject json) throws ParseException,
            JSONException {

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            try {
                String key = keys.next();

                JSONObject originalJSON = json.getJSONObject(key);
                if (Constants.TESTCASE.equalsIgnoreCase(key)) {
                    JSONArray jsonArray = new JSONArray();
                    Object testCaseObject = json.get(key);

                    if (!(testCaseObject instanceof JSONArray)) {
                        jsonArray.put(testCaseObject);

                    }
                    json.remove(Constants.TESTCASE);
                    json.put(key, jsonArray);
                }
            } catch (JSONException e) {
                //Do Nothing

            }

        }
        return json;
    }
}
