package jenkins.plugins.splunkins.SplunkLogging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

public class XmlParser {
	private ArrayList<JSONObject> jsonObjects = new ArrayList<JSONObject>();

	public void xmlParser(String xmlString, Logger logger) throws IOException, JSONException, ParseException{
		JSONObject xmlJSONObj = XML.toJSONObject(xmlString);
		ArrayList<JSONObject> list = parse(xmlJSONObj);

        for (JSONObject jsonObj : list) {
            logger.info(jsonObj.toString());
        }
	}

	private ArrayList<JSONObject> parse(JSONObject json)
			throws JSONException, ParseException {
		Iterator<String> keys = json.keys();
		JSONObject commonElements = new JSONObject();

		while (keys.hasNext()) {
			String key = keys.next();
			try {
				JSONObject originalJSON = json.getJSONObject(key);
				parse(originalJSON);
				commonElements = originalJSON;
			} catch (JSONException e) {
				if ("testcase".equalsIgnoreCase(key)) {
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
		commonElements.remove("testcase");
		return merge(commonElements, jsonObjects);
	}

	private ArrayList<JSONObject> merge(JSONObject obj1,
			ArrayList<JSONObject> obj2) throws JSONException {
		ArrayList<JSONObject> arr = new ArrayList<JSONObject>();

		for (int i = 0; i < obj2.size(); i++) {
			JSONObject json = new JSONObject();
			json.append("testsuite", obj1);
			json.append("testsuite", obj2.get(i));
			arr.add(json);

		}

		return arr;

	}
}
