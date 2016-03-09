package org.hspconsortium.platform.api.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.resource.Subscription;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

// todo this should change to be after the resource is saved, not before
@Component
public class SubscriptionSupportInterceptor extends InterceptorAdapter implements IServerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionSupportInterceptor.class);

    @Value("${hspc.platform.messaging.subscriptionSupport.subscriptionEndpoint}")
    private String subscriptionEndpoint;

    @Value("${hspc.platform.messaging.subscriptionSupport.resourceEndpoint}")
    private String resourceEndpoint;

    @Value("${hspc.platform.messaging.subscriptionSupport.enabled}")
    private String enabled;

    @Override
    public void incomingRequestPreHandled(RestOperationTypeEnum theOperation, ActionRequestDetails theRequestDetails) {
        if (Boolean.valueOf(enabled)) {
            IBaseResource iBaseResource = theRequestDetails.getResource();

            if (iBaseResource != null) {
                if (iBaseResource instanceof Subscription) {
                    LOGGER.info(prepareLogStatement(iBaseResource));
                    sendViaHTTP(iBaseResource, subscriptionEndpoint);
                } else {
                    LOGGER.info(prepareLogStatement(iBaseResource));
                    if (resourceEndpoint != null) {
                        sendViaHTTP(iBaseResource, resourceEndpoint);
                    } else {
                        LOGGER.warn("Resource messaging is not configured");
                    }
                }
            }
        }
    }

    /* Prepare a log statement with resource specific info.
         * Ignore NullPointerExceptions caused by missing data.
         * NOTE: This logging is for use during the pubsub demo. */
    private String prepareLogStatement(IBaseResource iBaseResource) {
        String logString;
        if (iBaseResource instanceof Patient) {
            logString = "\n\r" + this.getClass().getSimpleName() +
                    " handling Patient " + ((Patient) iBaseResource).getId();
            try {
                logString = logString + "\n\r    with last name " + ((Patient) iBaseResource).getName().get(0).getFamily().get(0);
            } catch (Exception ex) {
            }
            try {
                logString = logString + "\n\r    birth date " + ((Patient) iBaseResource).getBirthDate().toString();
            } catch (Exception ex) {
            }
        } else if (iBaseResource instanceof Observation) {
            logString = "\n\r" + this.getClass().getSimpleName() +
                    " handling Observation " + ((Observation) iBaseResource).getId();
            try {
                logString = logString + "\n\r    with code " + ((Observation) iBaseResource).getCode().getCoding().get(0).getCode();
            } catch (Exception ex) {
            }
            try {
                logString = logString + "\n\r    effective date " + ((Observation) iBaseResource).getEffective().toString();
            } catch (Exception ex) {
            }
        } else if (iBaseResource instanceof Subscription) {
            logString = "\n\r" + this.getClass().getSimpleName() +
                    " handling Subscription Id:" + ((Subscription) iBaseResource).getId();
            try {
                logString = logString + "\n\r    with criteria " + ((Subscription) iBaseResource).getCriteria();
            } catch (Exception ex) {
            }
            try {
                logString = logString + "\n\r    status " + ((Subscription) iBaseResource).getStatus();
            } catch (Exception ex) {
            }
        } else {
            logString = "\n\r" + this.getClass().getSimpleName() +
                    " handling Resource " + iBaseResource.toString();
        }
        return logString;
    }

    private void sendViaHTTP(IBaseResource iBaseResource, String endpoint) {
        HttpPost postRequest = new HttpPost(endpoint);
        postRequest.addHeader("Content-Type", "application/json");
        StringEntity entity = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(bytes);
            FhirContext.forDstu2().newJsonParser().encodeResourceToWriter(iBaseResource, writer);
            String jsonString = bytes.toString();
            entity = new StringEntity(bytes.toString());
            postRequest.setEntity(entity);

        } catch (IOException e) {
            // log and bury exception
            LOGGER.error("Error Sending Resource", e);
            return;
        }

        CloseableHttpClient httpClient = HttpClients.custom().setConnectionTimeToLive(30, TimeUnit.SECONDS).build();

//        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(postRequest)) {
        try {
            CloseableHttpResponse closeableHttpResponse = httpClient.execute(postRequest);
            if (closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                // log and bury exception
                LOGGER.error("Error Sending Resource.  Status Code: " + closeableHttpResponse.getStatusLine().getStatusCode());
            }
            closeableHttpResponse.close();
        } catch (IOException e) {
            // log and bury exception
            LOGGER.error("Error Sending Resource", e);
        }

    }

}
