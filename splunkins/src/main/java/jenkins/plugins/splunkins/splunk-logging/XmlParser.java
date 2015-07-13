import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.json.simple.parser.ParseException;

public class XmlParser {
	private ArrayList<JSONObject> jsonObjects = new ArrayList<JSONObject>();

	public void xmlParser(Logger logger) throws FileNotFoundException, IOException, JSONException, ParseException{
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(
				"/Users/kjotwani/Downloads/test-result.xml"))) {
			String sCurrentLine;
			while ((sCurrentLine = br.readLine()) != null) {
				sb.append(sCurrentLine);
			}

		}

		JSONObject xmlJSONObj = XML.toJSONObject(sb.toString());
		ArrayList<JSONObject> list = parse(xmlJSONObj);

		for (int i = 0; i < list.size(); i++)
			logger.info(list.get(i).toString());
	}

	private ArrayList<JSONObject> parse(JSONObject json)
			throws JSONException, ParseException {
		Iterator<String> keys = json.keys();
		JSONObject commonElements = new JSONObject();

		while (keys.hasNext()) {
			String key = keys.next();
			try {
				JSONObject originalJSON = json.getJSONObject(key.toString());
				parse(originalJSON);
				commonElements = originalJSON;
			} catch (JSONException e) {
				if ("testcase".equalsIgnoreCase(key.toString())) {
					JSONArray jsonarr = json.getJSONArray(key.toString());
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
