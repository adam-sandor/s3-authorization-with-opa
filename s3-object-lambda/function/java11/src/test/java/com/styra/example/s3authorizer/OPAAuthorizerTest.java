package com.styra.example.s3authorizer;

import com.bisnode.opa.client.query.OpaQueryApi;
import com.bisnode.opa.client.query.QueryForDocumentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OPAAuthorizerTest {

    @Test
    @DisplayName("Transform logic calls OPA and filters results")
    public void transformObjectResponseTest() throws Exception {
        //Todo: Rewrite this test based your transformation logic.

        byte[] responseInputStream = ("ID,Classification,Data\n" +
                "1,public,32rp98hp4qihfelrgjnp4938ghperoiksjdgnsfdlkg09\n" +
                "2,public,r43[209fjo[4ni[gdflkgldfkgjsfldgk]]]\n" +
                "3,sensitive,fdsagi[8ehgeihgnlfsdkjvlsdfkjhblksfdhgiosurghe4]").getBytes(StandardCharsets.UTF_8);

        OpaQueryApi queryApiMock = mock(OpaQueryApi.class);
        when(queryApiMock.queryForDocument(any(QueryForDocumentRequest.class), any(Class.class))).thenAnswer((Answer<JsonNode>) invocationOnMock -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue("{\n" +
                        "    \"allow\": true,\n" +
                        "    \"data_permissions\": [\n" +
                        "      \"public\"\n" +
                        "    ]\n" +
                        "  }", JsonNode.class);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });

        OPAAuthorizer opaAuthorizer = new OPAAuthorizer(queryApiMock);

        var transformedResponseObject = opaAuthorizer
                .authorizeObjectResponse(responseInputStream, null, null);
        var transformedString = new String(transformedResponseObject, StandardCharsets.UTF_8);
        assertEquals(("ID,Classification,Data\r\n" +
                "1,public,32rp98hp4qihfelrgjnp4938ghperoiksjdgnsfdlkg09\r\n" +
                "2,public,r43[209fjo[4ni[gdflkgldfkgjsfldgk]]]\r\n"), transformedString);
    }

    @Test
    @DisplayName("OPA replies with deny")
    public void denyResponse() throws Exception {
        try {
            byte[] responseInputStream = ("ID,Classification,Data\n" +
                    "1,public,32rp98hp4qihfelrgjnp4938ghperoiksjdgnsfdlkg09\n" +
                    "2,public,r43[209fjo[4ni[gdflkgldfkgjsfldgk]]]\n" +
                    "3,sensitive,fdsagi[8ehgeihgnlfsdkjvlsdfkjhblksfdhgiosurghe4]").getBytes(StandardCharsets.UTF_8);

            OpaQueryApi queryApiMock = mock(OpaQueryApi.class);
            when(queryApiMock.queryForDocument(any(QueryForDocumentRequest.class), any(Class.class))).thenAnswer((Answer<JsonNode>) invocationOnMock -> {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue("{\n" +
                            "  \"allow\": false,\n" +
                            "  \"data_permissions\": [\n" +
                            "    \"public\"\n" +
                            "  ],\n" +
                            "  \"deny\": [\n" +
                            "    \"org2 data can only be accessed from outside the US\"\n" +
                            "  ]\n" +
                            "}", JsonNode.class);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            OPAAuthorizer opaAuthorizer = new OPAAuthorizer(queryApiMock);

            opaAuthorizer.authorizeObjectResponse(responseInputStream, null, null);
        } catch (AuthorizationException ex) {
            assertEquals("[ \"org2 data can only be accessed from outside the US\" ]", ex.getMessage());
        }
    }
}
