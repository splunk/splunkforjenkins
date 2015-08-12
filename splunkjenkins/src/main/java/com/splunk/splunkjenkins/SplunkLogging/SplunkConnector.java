//package com.splunk.splunkjenkins.SplunkLogging;
//
//import com.splunk.*;
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.PrintStream;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.logging.Logger;
//
//public class SplunkConnector {
//    private final static Logger LOGGER = Logger.getLogger(SplunkConnector.class.getName());
//    private final PrintStream buildLogStream;
//    private ServiceArgs serviceArgs = new ServiceArgs();
//    private String splunkHost;
//    private int splunkSoapport;
//    private String splunkUsername;
//    private String splunkPassword;
//    private String splunkScheme;
//
//    public SplunkConnector(String splunkHost, int splunkSoapport,
//                           String splunkUsername, String splunkPassword, String splunkScheme, PrintStream buildLogStream) {
//        this.splunkHost = splunkHost;
//        this.splunkSoapport = splunkSoapport;  // Splunk Management Port
//        this.splunkUsername = splunkUsername;
//        this.splunkPassword = splunkPassword;
//        this.splunkScheme = splunkScheme;
//        this.buildLogStream = buildLogStream;
//    }
//
//    /**
//     * Enables the logging endpoint, checks if an httpinput exists, if true returns the token else creates a new httpInput and returns the token.
//     *
//     * @param httpinputName
//     * @return
//     * @throws IOException
//     */
////    public String createHttpinput(String httpinputName) throws IOException {
////        String token = "";
////        Service service = connectToSplunk();
////        
////        if (!checkIfHttpInputsIsEnabled(service)){
////            this.enableHttpinput(service);
////        }
////
////        Map args = new HashMap();
////        args.put("name", httpinputName);
////        args.put("description", "Http Input for Sending Data To Splunk");
////        
////        if (this.checkIfHttpInputExists(httpinputName, args, service)) {
////            token = this.getHttpInputToken(httpinputName, args, service);
////        } else {
////            ResponseMessage msg = service.post(Constants.httpInputTokenEndpointPath, args);
////            assert msg.getStatus() == 201;
////            args = new HashMap<>();
////            token = this.getHttpInputToken(httpinputName, args, service);
////
////        }
////      
////        return token;
////
////    }
//
//    public Service connectToSplunk() throws IOException {
//        HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2);
//        serviceArgs = getSplunkHostInfo();
//        String connectDebugMsg = "Connecting to Splunk with: " + serviceArgs.toString();
//        buildLogStream.write((connectDebugMsg+"\n").getBytes());
//
//        // get splunk service and login
//        Service service = Service.connect(serviceArgs);
//        service.login();
//        return service;
//    }
//
//    /**
//     * Wraps the splunk connection parameters in a serviceArgs object for the splunk sdk
//     */
//    public ServiceArgs getSplunkHostInfo() throws IOException {
//        if (serviceArgs.isEmpty()) {
//            serviceArgs.setHost(this.splunkHost);
//            serviceArgs.setPort(this.splunkSoapport);
//            serviceArgs.setUsername(this.splunkUsername);
//            serviceArgs.setPassword(this.splunkPassword);
//            serviceArgs.setScheme(this.splunkScheme);
//        }
//        return serviceArgs;
//    }
//
//    /**
//     * enable http input features
//     */
////    public void enableHttpinput(Service service) throws IOException {
////        // enable logging endpoint
////        Map<String, Object> args = new HashMap<>();
////        args.put("disabled", 0);
////        ResponseMessage response = service.post(Constants.httpInputCreateEndpoint, args);
////        assert response.getStatus() == 200;
////
////        args.clear();
////        args.put("index", "main");
////        ResponseMessage index_response = service.post(Constants.httpInputCreateEndpoint, args);
////        assert index_response.getStatus() == 200;
////    }
//
//    /**
//     * delete http input token
//     */
////    public void deleteHttpinput(String httpinputName, Service service) throws com.splunk.HttpException {
////        try {
////            ResponseMessage response = service.get(Constants.httpInputTokenEndpointPath + "/" + httpinputName);
////            if (response.getStatus() == 200) {
////                response = service.delete(Constants.httpInputTokenEndpointPath
////                        + "/" + httpinputName);
////                assert response.getStatus() == 200;
////            }
////        } catch (com.splunk.HttpException e) {
////            if (e.getStatus() != 404)
////                throw e;
////        }
////    }
//    
//    /**
//     * Checks if a give HttpInput exists
//     * @param httpinputName
//     * @param args
//     * @return boolean
//     * @throws IOException
//     */
////    private boolean checkIfHttpInputExists(String httpinputName, Map args, Service service) throws IOException{
////        
////        boolean httpInputExists = false;
////        try {
////            service.get(Constants.httpInputTokenEndpointPath + "/"
////                    + httpinputName, args);
////            httpInputExists = true;
////        }catch(HttpException e){
////            httpInputExists = false;
////        }
////
////        return httpInputExists;
////        
////    }
//    
//    /**
//     * Checks if the global HttpInput setting is enabled
//     * @param service
//     * @return
//     * @throws IOException
//     */
////    private boolean checkIfHttpInputsIsEnabled(Service service) throws IOException {
////        
////        boolean httpInputIsEnabled = false;
////        
////            ResponseMessage response = service.get(Constants.httpInputCreateEndpoint);
////            BufferedReader reader = null;
////
////            reader = new BufferedReader(new InputStreamReader(
////                    response.getContent(), "UTF-8"));
////            String disabled = "";
////            while (true) {
////                String line = null;
////                line = reader.readLine();
////                if (line == null)
////                    break;
////
////                if (line.contains("name=\"disabled\"")) {
////                    disabled = line.split(">")[1];
////                    disabled = disabled.split("<")[0];
////                    break;
////                }
////            }
////            reader.close();
////            
////            if (Integer.parseInt(disabled.toString().trim())==0){
////                httpInputIsEnabled = true;
////            }else{
////                httpInputIsEnabled = false;
////            }
////            
////            return httpInputIsEnabled;
////                   
////    }
//    
//    /**
//     * Get the Token for the given HttpInput
//     * 
//     * @param httpinputName
//     * @param args
//     * @param service
//     * @return token
//     * @throws IOException
//     */
//    public String getHttpInputToken(String httpinputName, Map args, Service service) throws IOException{
//        
//        ResponseMessage response = service.get(
//                Constants.httpInputTokenEndpointPath + "/" + httpinputName,
//                args);
//            BufferedReader reader = null;
//
//            reader = new BufferedReader(new InputStreamReader(
//                    response.getContent(), "UTF-8"));
//            String token = "";
//            while (true) {
//                String line = null;
//                line = reader.readLine();
//                if (line == null)
//                    break;
//
//                if (line.contains("name=\"token\"")) {
//                    token = line.split(">")[1];
//                    token = token.split("<")[0];
//                    break;
//                }
//            }
//            reader.close();
//            return token;
//
//        }
//    
//
//}
