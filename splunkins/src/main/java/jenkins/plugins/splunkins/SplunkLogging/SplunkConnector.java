package jenkins.plugins.splunkins.SplunkLogging;

import com.splunk.*;
import jenkins.plugins.splunkins.SplunkinsInstallation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class SplunkConnector {
    private static Service service;
    private static final ServiceArgs serviceArgs = new ServiceArgs();

    public static String createHttpinput(String httpinputName) throws Exception {
        connectToSplunk();

        // enable logging endpoint
        SplunkConnector.enableHttpinput();

        // create a httpinput
        Map args = new HashMap();
        args.put("name", httpinputName);
        args.put("description", "test http input");

        deleteHttpinput(httpinputName);

        ResponseMessage msg = service.post(Constants.httpInputTokenEndpointPath, args);
        assert msg.getStatus() == 201;

        // get httpinput token
        args = new HashMap();
        ResponseMessage response = service.get(Constants.httpInputTokenEndpointPath + "/"
                + httpinputName, args);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                response.getContent(), "UTF-8"));
        String token = "";
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;

            if (line.contains("name=\"token\"")) {
                token = line.split(">")[1];
                token = token.split("<")[0];
                break;
            }
        }
        reader.close();

        if (token.isEmpty()) {
            System.out.println("No ");
        }
        return token;
    }

    public static Service connectToSplunk() throws IOException {

        if (service == null) {
            HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2);
            getSplunkHostInfo();

            // get splunk service and login
            service = Service.connect(serviceArgs);
            service.login();
        }
        return service;
    }

    /**
     * Get the splunk host info from the global configuration page
     */
    public static ServiceArgs getSplunkHostInfo() throws IOException {

        if (serviceArgs.isEmpty()) {
            // set default value
            SplunkinsInstallation.Descriptor descriptor = SplunkinsInstallation.getSplunkinsDescriptor();
            serviceArgs.setHost(descriptor.host);
            serviceArgs.setPort(descriptor.port);
            serviceArgs.setUsername(descriptor.username);
            serviceArgs.setPassword(descriptor.password);
            serviceArgs.setScheme(descriptor.scheme);
        }
        return serviceArgs;
    }

    /**
     * enable http input features
     */
    public static void enableHttpinput() throws IOException {
        connectToSplunk();

        // enable logging endpoint
        Map args = new HashMap();
        args.put("disabled", 0);
        ResponseMessage response = service.post(
                Constants.httpInputCreateEndpoint, args);
        assert response.getStatus() == 200;

        args.clear();
        args.put("index", "main");
        ResponseMessage index_response = service.post(
                Constants.httpInputCreateEndpoint, args);
        assert index_response.getStatus() == 200;
    }

    /**
     * delete http input token
     */
    public static void deleteHttpinput(String httpinputName) throws Exception {
        connectToSplunk();
        try {
            ResponseMessage response = service.get(Constants.httpInputTokenEndpointPath
                    + "/" + httpinputName);
            if (response.getStatus() == 200) {
                response = service.delete(Constants.httpInputTokenEndpointPath + "/"
                        + httpinputName);
                assert response.getStatus() == 200;
            }
        } catch (com.splunk.HttpException e) {
            if (e.getStatus() != 404)
                throw e;
        }
    }
}
