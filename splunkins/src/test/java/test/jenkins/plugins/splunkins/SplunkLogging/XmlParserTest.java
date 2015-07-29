package test.jenkins.plugins.splunkins.SplunkLogging; 

import java.util.ArrayList;

import jenkins.plugins.splunkins.SplunkLogging.XmlParser;

import org.json.JSONObject;
import org.junit.Test; 
import org.junit.Before; 
import org.junit.After; 
import org.mockito.Mockito;

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
public void testMerge() throws Exception { 
    
    XmlParser parser = new XmlParser();
    JSONObject jsonObj1 = new JSONObject("{\"time\":1.16109848022E-4,\"classname\":\"test_me\",\"name\":\"test_me\"}");
    JSONObject jsonObj2 = new JSONObject("{\"failures\":0,\"time\":0.008,\"errors\":0,\"tests\":1,\"name\":\"pytest\",\"skips\":0}");

    ArrayList<JSONObject> jsonObjList = new ArrayList<JSONObject>();
    jsonObjList.add(jsonObj2);
    
    
    System.out.println(parser.merge(jsonObj1, jsonObjList));
    

} 

/** 
* 
* Method: validateXMLSchema(String xsdPath, String xmlString) 
* 
*/ 
@Test
public void testValidateXMLSchema(){ 
//TODO: Test goes here... 

    XmlParser parser = new XmlParser();
    String xmlStringPass = "<?xml version=\"1.0\" encoding=\"utf-8\"?><testsuite errors=\"0\" failures=\"0\" name=\"pytest\" skips=\"0\" tests=\"1\" time=\"0.008\"><testcase classname=\"test_me\" name=\"test_me\" time=\"0.000116109848022\"/></testsuite>";
    boolean parsePass = parser.validateXMLSchema("/Users/kjotwani/Desktop/SplunkStuff/qti_stuff/splunkins/src/main/webapp/junit.xsd", xmlStringPass);
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
