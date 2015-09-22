package com.splunk.splunkjenkins.SplunkLogging;


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
    private boolean entryOnce;

    /**
     * Parses the input XML to a JSON and then massages the JSON object as per requirement.
     *
     * @param logs
     * @return
     */
    public JSONObject xmlParser(String logs) {
        Object xmlJSONObj = null;
        JSONObject splunkJSON = null;

        try {
            if (validateXMLSchema(Constants.xsdPath, logs)){
                xmlJSONObj = (JSONObject) XML.toJSONObject(logs);
            }
            else {
                //TODO: Add parsing for other files
            }
            if (xmlJSONObj instanceof JSONObject) {
                splunkJSON = parse((JSONObject) xmlJSONObj);
                return splunkJSON;

            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
        return splunkJSON;
    }

    /**
     * Create a custom JSON object that is needed to be send to Splunk.
     * 
     * @param json
     * @return
     * @throws JSONException
     * @throws ParseException
     */
    private JSONObject parse(JSONObject json) throws JSONException,
            ParseException {

        JSONObject transformedJSON = null;
        Iterator<String> originalKeys = json.keys();
        while (originalKeys.hasNext()) {
            String key = originalKeys.next();
            transformedJSON = customJSONObject(json.getJSONObject(key));
        }

        return transformedJSON;
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

        if (!jsonObjList.isEmpty() & jsonObj1.length()!=0) {
            for (int i = 0; i < jsonObjList.size(); i++) {
                JSONObject json = new JSONObject();
                JSONObject obj1Copy = new JSONObject(jsonObj1, JSONObject.getNames(jsonObj1));
                JSONObject parentJSONValue = obj1Copy.put(Constants.TESTCASE, jsonObjList.get(i).getJSONObject(Constants.TESTCASE));
                json.put(Constants.TESTSUITE, parentJSONValue);
                arr.add(json);

            }
        }else{
            JSONObject json = new JSONObject();
            json.put(Constants.TESTSUITE, jsonObj1);
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
