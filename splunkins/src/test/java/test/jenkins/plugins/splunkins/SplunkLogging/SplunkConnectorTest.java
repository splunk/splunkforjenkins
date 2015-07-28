package test.jenkins.plugins.splunkins.SplunkLogging; 

import hudson.model.AbstractBuild;
import hudson.model.Result;
import jenkins.plugins.splunkins.SplunkLogging.SplunkConnector;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

/** 
* SplunkConnector Tester. 
* 
* @author <Authors name> 
* @since <pre>Jul 27, 2015</pre> 
* @version 1.0 
*/ 
public class SplunkConnectorTest {
    SplunkConnector connector;

@Before
public void before() throws Exception {
    PrintStream build = Mockito.mock(PrintStream.class);
    this.connector = new SplunkConnector("127.0.0.1", 8089, "admin", "changed", "https", build);
} 

@After
public void after() throws Exception { 
} 

/** 
* 
* Method: createHttpinput(String httpinputName) 
* 
*/ 
@Test
public void testCreateHttpinput() throws Exception { 
//TODO: Test goes here...
//    String token = connector.createHttpinput("httpinput_name");
//    assert token;
}


/**
*
* Method: createHttpinput(String httpinputName)
 * Tests the parse token method by mocking the BufferedReader it receives.
*
*/
@Test
public void testParseToken() throws Exception {
    BufferedReader reader = Mockito.mock(BufferedReader.class);
    Mockito.when(reader.readLine()).thenReturn("<?xml-stylesheet type=\"text/xml\" href=\"/static/atom.xsl\"?>",
                                               "        </s:key",
                                               "<s:key name=\"token\">BF0C84FA-9D40-487C-8624-7EDD9B8EE33A</s:key>");
    String token = SplunkConnector.parseToken(reader);
    assert token.equalsIgnoreCase("BF0C84FA-9D40-487C-8624-7EDD9B8EE33A");
}


/**
* 
* Method: connectToSplunk() 
* 
*/ 
@Test
public void testConnectToSplunk() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getSplunkHostInfo() 
* 
*/ 
@Test
public void testGetSplunkHostInfo() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: enableHttpinput(Service service) 
* 
*/ 
@Test
public void testEnableHttpinput() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: deleteHttpinput(String httpinputName, Service service) 
* 
*/ 
@Test
public void testDeleteHttpinput() throws Exception { 
//TODO: Test goes here... 
} 


} 
