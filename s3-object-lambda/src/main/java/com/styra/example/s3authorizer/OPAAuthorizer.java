package com.styra.example.s3authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3ObjectLambdaEvent;
import com.bisnode.opa.client.OpaClient;
import com.bisnode.opa.client.query.OpaQueryApi;
import com.bisnode.opa.client.query.QueryForDocumentRequest;
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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OPAAuthorizer {

    private OpaQueryApi opaQueryApi;

    private final String OPA_URL = "http://a440534714464416bbac96780e5aded1-221080361.us-east-1.elb.amazonaws.com:8181";

    private final Logger logger = LoggerFactory.getLogger(AuthorizationException.class);

    public OPAAuthorizer() {
        this.opaQueryApi = OpaClient.builder()
                .opaConfiguration(OPA_URL)
                .build();
    }

    public OPAAuthorizer(OpaQueryApi opaQueryApi) {
        this.opaQueryApi = opaQueryApi;
    }

    public byte[] authorizeObjectResponse(byte[] responseObjectByteArray, S3ObjectLambdaEvent event, Context context) throws Exception {
        String responseFile = new String(responseObjectByteArray, StandardCharsets.UTF_8);
        logger.info("Transforming file: {}", responseFile);

        ObjectNode input = new ObjectMapper().createObjectNode();
        //context and event can be null in tests
        if (context != null) {
            input.putPOJO("client_context", context.getClientContext());
            input.putPOJO("function_name", context.getFunctionName());
        }
        if (event != null) {
            input.putPOJO("user_request", event.getUserRequest());
            input.putPOJO("userIdentity", event.getUserIdentity());
        }

        logger.info("Making call to OPA at {}", OPA_URL);
        JsonNode result = opaQueryApi.queryForDocument(new QueryForDocumentRequest(input, "rules"), JsonNode.class);
        boolean allowed = result.get("allow").asBoolean();
        if (!allowed) {
            throw new AuthorizationException(result.get("deny").toPrettyString());
        }

        ArrayNode dataPermissionsNode = (ArrayNode) result.get("data_permissions");
        List<String> dataPermissions = new ArrayList<>();
        dataPermissionsNode.elements().forEachRemaining(perm -> dataPermissions.add(perm.asText()));
        logger.info("Received following permissions from OPA {}", dataPermissions);

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
    }
}
