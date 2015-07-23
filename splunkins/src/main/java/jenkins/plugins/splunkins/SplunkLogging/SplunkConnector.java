package jenkins.plugins.splunkins.SplunkLogging;

import com.splunk.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class SplunkConnector {
    private static Service splunkService;
    private static final ServiceArgs serviceArgs = new ServiceArgs();
    private String splunkHost;
    private int splunkSoapport;
    private String splunkUsername;
    private String splunkPassword;
    private String splunkScheme;

    public SplunkConnector(String splunkHost, int splunkSoapport,
                           String splunkUsername, String splunkPassword, String splunkScheme) {
        this.splunkHost = splunkHost;
        this.splunkSoapport = splunkSoapport;  // Splunk Management Port
        this.splunkUsername = splunkUsername;
        this.splunkPassword = splunkPassword;
        this.splunkScheme = splunkScheme;

    }

    /**
     * Enables the logging endpoint, creates an httpinput, and returns the token.
     *
     * @param httpinputName
     * @return
     * @throws Exception
     */
    public String createHttpinput(String httpinputName) throws Exception {
        Service service = connectToSplunk();

        this.enableHttpinput(service);

        // create a httpinput
        Map<String, Object> args = new HashMap<>();
        args.put("name", httpinputName);
        args.put("description", "test http input");

        deleteHttpinput(httpinputName, service);

        ResponseMessage msg = service.post(Constants.httpInputTokenEndpointPath, args);
        assert msg.getStatus() == 201;

        // get httpinput token
        args = new HashMap<>();
        ResponseMessage response = service.get(Constants.httpInputTokenEndpointPath + "/" + httpinputName, args);
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getContent(), "UTF-8"));
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

        if (token.isEmpty()) {   // TODO: improve condition handling
            System.out.println("No");
        }
        return token;
    }

    public Service connectToSplunk() throws IOException {
        Service service = null;
        HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2);
        getSplunkHostInfo();

        // get splunk service and login
        service = Service.connect(serviceArgs);
        service.login();
        return service;
    }

    /**
     * Wraps the splunk connection parameters in a serviceArgs object for the splunk sdk
     */
    public ServiceArgs getSplunkHostInfo() throws IOException {

        if (serviceArgs.isEmpty()) {
            serviceArgs.setHost(this.splunkHost);
            serviceArgs.setPort(this.splunkSoapport);
            serviceArgs.setUsername(this.splunkUsername);
            serviceArgs.setPassword(this.splunkPassword);
            serviceArgs.setScheme(this.splunkScheme);
        }
        return serviceArgs;
    }

    /**
     * enable http input features
     */
    public void enableHttpinput(Service service) throws IOException {
        // enable logging endpoint
        Map<String, Object> args = new HashMap<>();
        args.put("disabled", 0);
        ResponseMessage response = service.post(Constants.httpInputCreateEndpoint, args);
        assert response.getStatus() == 200;

        args.clear();
        args.put("index", "main");
        ResponseMessage index_response = service.post(Constants.httpInputCreateEndpoint, args);
        assert index_response.getStatus() == 200;
    }

    /**
     * delete http input token
     */
    public void deleteHttpinput(String httpinputName, Service service) throws Exception {
        try {
            ResponseMessage response = service.get(Constants.httpInputTokenEndpointPath + "/" + httpinputName);
            if (response.getStatus() == 200) {
                response = service.delete(Constants.httpInputTokenEndpointPath
                        + "/" + httpinputName);
                assert response.getStatus() == 200;
            }
        } catch (com.splunk.HttpException e) {
            if (e.getStatus() != 404)
                throw e;
        }
    }
}
