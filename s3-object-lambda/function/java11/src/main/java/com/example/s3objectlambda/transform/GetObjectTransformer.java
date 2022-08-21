package com.example.s3objectlambda.transform;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3ObjectLambdaEvent;
import com.bisnode.opa.client.OpaClient;
import com.bisnode.opa.client.query.OpaQueryApi;
import com.bisnode.opa.client.query.QueryForDocumentRequest;
import com.example.s3objectlambda.exception.TransformationException;
import com.example.s3objectlambda.request.GetObjectRequestWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This is the transformer class for getObject requests.
 */

public class GetObjectTransformer implements Transformer {
    private GetObjectRequestWrapper userRequest;

    private Context context;

    private S3ObjectLambdaEvent event;

    private OpaQueryApi opaQueryApi;

    private final String OPA_URL="http://a440534714464416bbac96780e5aded1-221080361.us-east-1.elb.amazonaws.com:8181";

    private static final Logger log = LoggerFactory.getLogger(GetObjectTransformer.class);

    public GetObjectTransformer(GetObjectRequestWrapper userRequest, Context context, S3ObjectLambdaEvent event) {
        this.userRequest = userRequest;
        this.opaQueryApi = OpaClient.builder()
                .opaConfiguration(OPA_URL)
                .build();
        this.context = context;
        this.event = event;
    }

    /**
     * TODO: Implement your transform object logic here.
     *
     * @param responseObjectByteArray object response as byte array to be transformed.
     * @return Transformed object as byte array.
     */
    public byte[] transformObjectResponse(byte[] responseObjectByteArray) throws TransformationException {
        String responseFile = new String(responseObjectByteArray, StandardCharsets.UTF_8);
        log.info("Transforming file: {}", responseFile);
        try {
            ObjectNode input = new ObjectMapper().createObjectNode();
            input.putPOJO("user_request", userRequest.getUserRequest());
            if (context != null) {
                input.putPOJO("client_context", context.getClientContext());
                input.putPOJO("function_name", context.getFunctionName());
            }
            if (event != null) {
                input.putPOJO("userIdentity", event.getUserIdentity());
            }

            log.info("Making call to OPA at {}", OPA_URL);
            JsonNode result = opaQueryApi.queryForDocument(new QueryForDocumentRequest(input, "rules"), JsonNode.class);
            Boolean allowed = result.get("allow").asBoolean();
            if (!allowed) {
                throw new AuthorizationException(result.get("deny").toPrettyString());
            }

            ArrayNode dataPermissionsNode = (ArrayNode) result.get("data_permissions");
            List<String> dataPermissions = new ArrayList<>();
            dataPermissionsNode.elements().forEachRemaining(perm -> dataPermissions.add(perm.asText()));
            log.info("Received following permissions from OPA {}", dataPermissions);

            CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader("ID", "Classification", "Data");
            CSVParser parser = csvFormat.parse(new StringReader(responseFile));

            StringBuilder responseString = new StringBuilder();
            CSVPrinter printer = csvFormat.print(responseString);

            for (CSVRecord record : parser.getRecords()) {
                if (dataPermissions.contains(record.get("Classification"))) {
                    printer.printRecord(record.toList());
                }
            }

            return responseString.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException ioex) {
            throw new TransformationException(ioex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new TransformationException("Error while parsing file:\n" + responseFile);
        }
    }

    public void setOpaQueryApi(OpaQueryApi opaQueryApi) {
        this.opaQueryApi = opaQueryApi;
    }
}
