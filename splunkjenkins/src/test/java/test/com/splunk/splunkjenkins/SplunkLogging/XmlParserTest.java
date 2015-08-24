package test.com.splunk.splunkjenkins.SplunkLogging;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import com.splunk.splunkjenkins.SplunkLogging.XmlParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test; 
import org.junit.Before; 
import org.junit.After;

/** 
* XmlParser Tester. 
* 
* @author <Authors name> 
* @since <pre>Jul 22, 2015</pre> 
* @version 1.0 
*/ 
public class XmlParserTest { 

@Before
public void before() throws Exception { 
} 

@After
public void after() throws Exception { 
} 

/** 
* 
* Method: xmlParser(String logs) 
* 
*/ 
@Test
public void testXmlParser() throws Exception { 
//TODO: Test goes here... 
} 


/** 
* 
* Method: parse(JSONObject json) 
* 
*/ 
@Test
public void testParse() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = XmlParser.getClass().getMethod("parse", JSONObject.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: merge(JSONObject jsonObj1, ArrayList<JSONObject> jsonObjList) 
* 
*/ 
@Test
@Ignore
public void testMerge() throws Exception { 
    
    XmlParser parser = new XmlParser();
    JSONObject jsonObj1 = new JSONObject("{\"testcase\":{\"time\":0.0813760757446,\"name\":\"test_basic_alert\",\"classname\":\"test_alert.TestAlerts\"}}");
    JSONObject jsonObj2 = new JSONObject("{\"failures\":0,\"time\":0.008,\"errors\":0,\"tests\":1,\"name\":\"pytest\",\"skips\":0}");

    ArrayList<JSONObject> jsonObjList = new ArrayList<JSONObject>();
    jsonObjList.add(jsonObj2);
    
    JSONObject result = new JSONObject("{\"testsuite\":{\"failures\":0,\"time\":0.008,\"errors\":0,\"tests\":1,\"name\":\"pytest\",\"skips\":0,\"testcase\":{\"time\":0.0813760757446,\"classname\":\"test_alert.TestAlerts\",\"name\":\"test_basic_alert\"}}}");
    
        for (JSONObject jsonOutput : parser.merge(jsonObj1, jsonObjList)) {
            Iterator<String> keys = result.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray array1 = result.getJSONArray(key);
                //assert (result.getJSONArray(key)..toString().equalsIgnoreCase(jsonOutput.getJSONArray(key).toString()));

            }
        }
    

} 

/** 
* 
* Method: validateXMLSchema(String xsdPath, String xmlString) 
* 
*/ 
@Test
public void testValidateXMLSchema(){ 
    XmlParser parser = new XmlParser();
    String xmlStringPass = "<?xml version=\"1.0\" encoding=\"utf-8\"?><testsuite errors=\"0\" failures=\"0\" name=\"pytest\" skips=\"0\" tests=\"1\" time=\"0.008\"><testcase classname=\"test_me\" name=\"test_me\" time=\"0.000116109848022\"/></testsuite>";

    // Get junit schema file
    URL url = this.getClass().getResource(File.separator+"junit.xsd");
    File schemaFile = new File(url.getFile());

    // Test string against junit xml file
    boolean parsePass = parser.validateXMLSchema(schemaFile.getPath(), xmlStringPass);
    assert parsePass;
}

/** 
* 
* Method: customJSONObject(JSONObject json) 
* 
*/ 
@Test
public void testCustomJSONObject() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = XmlParser.getClass().getMethod("customJSONObject", JSONObject.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

} 
