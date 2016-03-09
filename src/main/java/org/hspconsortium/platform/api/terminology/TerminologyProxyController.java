package org.hspconsortium.platform.api.terminology;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@RestController
public class TerminologyProxyController {

    @Value("${hspc.platform.api.fhir.terminologyEndpointURL}")
    private String terminologyEndpointURL;

    @Value("${hspc.platform.api.fhir.federatedEndpointURL}")
    private String federatedEndpointURL;

    @RequestMapping(value = "/terminology/health", method = RequestMethod.GET)
    public String health(HttpServletRequest request, HttpServletResponse response) {
        return "OK";
    }

    // Federation Proxy
    @RequestMapping(value = "/federated", method = RequestMethod.GET)
    public void handleFederatedRequest(HttpServletRequest request, HttpServletResponse response) {
        Map parameters = request.getParameterMap();
        HttpGet getRequest = null;
        try {
            getRequest = new HttpGet(configureBuilder(parameters, this.federatedEndpointURL).build());
        } catch (URISyntaxException e) {
            throw new RuntimeException(
                    String.format("There was an error creating the Http Request.\n" +
                            "Error : %s ."
                            , e.getCause()));
        }
        getRequest.addHeader("Accept", "application/json");

        CloseableHttpClient httpClient = HttpClients.custom().build();

        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(getRequest)) {
            if(closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                HttpEntity rEntity = closeableHttpResponse.getEntity();
                String responseString = EntityUtils.toString(rEntity, "UTF-8");
                throw new RuntimeException(String.format("There was a problem contacting the terminology server.\n" +
                        "Response Status : %s .\nResponse Detail :%s."
                        , closeableHttpResponse.getStatusLine()
                        , responseString));
            }
            response.setHeader("Content-Type", "application/json;charset=utf-8");
            response.getWriter().write( EntityUtils.toString(closeableHttpResponse.getEntity()));
        } catch (IOException io_ex) {
            throw new RuntimeException(io_ex);
        }
    }


    // Terminology Wrapper
    @RequestMapping(value = "/terminology", method = RequestMethod.GET)
    public void handleLaunchRequest(HttpServletRequest request, HttpServletResponse response) {
        Map parameters = request.getParameterMap();
        HttpGet getRequest = null;
        try {
            getRequest = new HttpGet(configureBuilder(parameters, this.terminologyEndpointURL).build());
        } catch (URISyntaxException e) {
            throw new RuntimeException(
                    String.format("There was an error creating the Http Request.\n" +
                    "Error : %s ."
                    , e.getCause()));
        }
        getRequest.addHeader("Accept", "application/json");

        CloseableHttpClient httpClient = HttpClients.custom().build();

        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(getRequest)) {
            if(closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                HttpEntity rEntity = closeableHttpResponse.getEntity();
                String responseString = EntityUtils.toString(rEntity, "UTF-8");
                throw new RuntimeException(String.format("There was a problem contacting the terminology server.\n" +
                        "Response Status : %s .\nResponse Detail :%s."
                        , closeableHttpResponse.getStatusLine()
                        , responseString));
            }
            response.setHeader("Content-Type", "application/json;charset=utf-8");
            response.getWriter().write( EntityUtils.toString(closeableHttpResponse.getEntity()));
        } catch (IOException io_ex) {
            throw new RuntimeException(io_ex);
        }
    }

    private URIBuilder configureBuilder(Map parameters, String endpoint) {
        String[] uri = (String[])parameters.get("uri");
        String[] pathAndQuery = uri[0].split("\\?");

        List<NameValuePair> nameValuePairs = new ArrayList<>();
        String[] params = pathAndQuery[1].split("&");
        for(String keyValuePair : params) {
            String keyValuePairArray[] = keyValuePair.split("=");
            nameValuePairs.add(new BasicNameValuePair(keyValuePairArray[0],keyValuePairArray[1]));
        }

        URIBuilder builder = new URIBuilder();
        builder.setScheme("http").setHost(endpoint).setPath(pathAndQuery[0])
                .setParameters(nameValuePairs);

        return builder;
    }
}
