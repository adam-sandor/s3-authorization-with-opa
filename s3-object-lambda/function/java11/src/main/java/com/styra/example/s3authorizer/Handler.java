package com.styra.example.s3authorizer;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.WriteGetObjectResponseRequest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3ObjectLambdaEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

public class Handler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private final OPAAuthorizer opaAuthorizer = new OPAAuthorizer();

    public void handleRequest(S3ObjectLambdaEvent event, Context context) throws Exception {
        if (event.getGetObjectContext() == null) return;

        try {
            // Get the original object from Amazon S3
            HttpResponse<InputStream> presignedResponse = getS3ObjectResponse(event);
            if (presignedResponse.statusCode() >= 400) {
                throw new IllegalStateException("Error status when getting original S3 response: " + presignedResponse.statusCode());
            }

            byte[] objectResponseByteArray = presignedResponse.body().readAllBytes();

            byte[] transformedObject = opaAuthorizer.authorizeObjectResponse(objectResponseByteArray, event, context);
            writeObjectResponse(presignedResponse, event, transformedObject);
        } catch (AuthorizationException e) {
            logger.error("Authorization error by OPA: {}", e.getMessage());
            writeErrorResponse(e.getMessage(), Error.UNAUTHORIZED, event);
        } catch (Exception e) {
            logger.error("Error while authorizing S3 request", e);
            writeErrorResponse(e.getMessage(), Error.SERVER_ERROR, event);
        }
    }

    private HttpResponse<InputStream> getS3ObjectResponse(S3ObjectLambdaEvent event)
            throws URISyntaxException, IOException, InterruptedException {
        var httpRequestBuilder = HttpRequest.newBuilder(new URI(event.inputS3Url()));
        var userRequestHeaders = event.getUserRequest().getHeaders();
        var headersToBePresigned = Arrays.asList(
                "x-amz-checksum-mode",
                "x-amz-request-payer",
                "x-amz-expected-bucket-owner",
                "If-Match",
                "If-Modified-Since",
                "If-None-Match",
                "If-Unmodified-Since");

        for (var userRequestHeader : userRequestHeaders.entrySet()) {
            if (headersToBePresigned.contains(userRequestHeader.getKey())) {
                httpRequestBuilder.header(userRequestHeader.getKey(), userRequestHeader.getValue());
            }
        }
        var presignedResponse = this.httpClient.send(
                httpRequestBuilder.GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        return presignedResponse;
    }

    public void writeErrorResponse(String errorMessage, Error error, S3ObjectLambdaEvent event) {

        this.s3Client.writeGetObjectResponse(new WriteGetObjectResponseRequest()
                .withRequestRoute(event.outputRoute())
                .withRequestToken(event.outputToken())
                .withErrorCode(error.getErrorCode())
                .withContentLength(0L).withInputStream(new ByteArrayInputStream(new byte[0]))
                .withErrorMessage(errorMessage)
                .withStatusCode(error.getStatusCode()));
    }

    public void writeObjectResponse(HttpResponse<InputStream> presignedResponse, S3ObjectLambdaEvent event,
                                    byte[] responseObjectByteArray) throws Exception {

        String checksum = calculateMd5Checksum(responseObjectByteArray);

        var checksumMap = new HashMap<String, String>();
        checksumMap.put("algorithm", "MD5");
        checksumMap.put("digest", checksum);

        var checksumObjectMetaData = new ObjectMetadata();
        checksumObjectMetaData.setUserMetadata(checksumMap);

        this.s3Client.writeGetObjectResponse(new WriteGetObjectResponseRequest()
                .withRequestRoute(event.outputRoute())
                .withRequestToken(event.outputToken())
                .withInputStream(new ByteArrayInputStream(responseObjectByteArray))
                .withMetadata(checksumObjectMetaData)
                .withStatusCode(presignedResponse.statusCode()));
    }

    public static String calculateMd5Checksum(byte[] objectResponse) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        var digest = md.digest(objectResponse);

        return Base64.getEncoder().encodeToString(digest);
    }
}
