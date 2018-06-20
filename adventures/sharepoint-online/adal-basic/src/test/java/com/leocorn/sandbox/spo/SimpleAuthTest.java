package com.leocorn.sandbox.spo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import javax.naming.ServiceUnavailableException;

import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;

import org.json.JSONObject;
import org.json.JSONArray;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class SimpleAuthTest extends TestCase {


    private Properties conf = new Properties();

    public SimpleAuthTest(String testName) {

        super(testName);
        try {
            conf = loadConfig();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static Test suite() {

        return new TestSuite(SimpleAuthTest.class);
    }

    public void testListFiles() throws Exception {

        String accessToken = getAuthResult().getAccessToken();
        String apiUri = "/_api/web/GetFolderByServerRelativeUrl('Customer%20Group%20K/Karl%20Dungs%20Inc%20-%200004507796/000070008273')/files";

        JSONObject json = new JSONObject(getResponse(accessToken, apiUri));
        System.out.println(json.toString(2));
        System.out.println(json.getString("odata.metadata"));
        JSONArray jsonArray = json.getJSONArray("value");
        for (int index = 0; index < jsonArray.length(); index++) {

            System.out.println(jsonArray.getJSONObject(index).getString("Title"));
        }
    }

    private String getResponse(String accessToken, String apiUri) throws Exception {

        URL url = new URL(conf.getProperty("target.source") + 
                          conf.getProperty("sharepoint.site") + apiUri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept","application/json");

        int httpResponseCode = conn.getResponseCode();
        if(httpResponseCode == 200) {
            BufferedReader in = null;
            StringBuilder response;
            try{
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                String inputLine;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                return response.toString();
            } finally {
                in.close();
            }
        } else {
            System.out.println(String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage()));
            return null;
        }
    }

    /**
     * simple test case to login and get access token.
     */
    public void testGetToken() {

        AuthenticationResult result = getAuthResult();
        assertNotNull(result);
        System.out.println(result.getAccessToken());
    }

    private AuthenticationResult getAuthResult() {

        // load the config file.
        ExecutorService service = null;
        AuthenticationResult result = null;

        try {

            assertEquals("sharepoint online", conf.getProperty("name"));

            AuthenticationContext context;

            // try to authenicate and acquire token.
            service = Executors.newFixedThreadPool(1);

            context = new AuthenticationContext(conf.getProperty("authority"),
                                                false, service);
            Future<AuthenticationResult> future = 
                context.acquireToken(conf.getProperty("target.source"),
                                     conf.getProperty("application.id"),
                                     conf.getProperty("username"),
                                     conf.getProperty("password"), null);
            result = future.get();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ix){
            ix.printStackTrace();
        } catch (ExecutionException ix){
            ix.printStackTrace();
        } finally{
            // shutdown the executor!
            service.shutdown();
        }

        return result;
    }

    /**
     * a utility method to load configuration files.
     */
    private Properties loadConfig() throws IOException {
        /**
         * file basic.properties will have the following content:
         */
        String filename = "conf/spo.properties";
        String localFilename = "conf/local.properties";
        Properties conf = new Properties();
        InputStream input = null;

        try {
            // load the basic properties.
            input = getClass().getClassLoader().getResourceAsStream(filename);
            Properties basic = new Properties();
            basic.load(input);
            input.close();
            conf.putAll(basic);

            // load the local properties.
            input = getClass().getClassLoader().getResourceAsStream(localFilename);
            if(input != null) {
                Properties local = new Properties();
                local.load(input);
                input.close();
                conf.putAll(local);
            } else {
                // null input stream means the file is not exist.
                // just skip it!
            }

            assertEquals("sharepoint online", conf.getProperty("name"));

            return conf;
        } finally{
            if(input!=null){
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
