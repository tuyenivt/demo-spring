package com.example.versioning;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class VersioningIntegrationTest {

    // RFC 9745 Deprecation (@epoch-seconds) and RFC 8594 Sunset (HTTP-date) values configured in ApiVersionConfig
    static final String V1_DEPRECATION = "@1735689600";
    static final String V1_SUNSET = "Wed, 31 Dec 2025 23:59:59 GMT";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class UriPathVersioning {

        @Test
        void shouldRouteToV2ByPath() throws Exception {
            mockMvc.perform(get("/v2/schedule"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("v2")));
        }

        @Test
        void shouldIncludeDeprecationHeadersForV1() throws Exception {
            mockMvc.perform(get("/v1/employees"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Deprecation", V1_DEPRECATION))
                    .andExpect(header().string("Sunset", V1_SUNSET))
                    .andExpect(header().string("Link", "</api/versions>; rel=\"deprecation\"; type=\"text/html\""));
        }

        @Test
        void shouldNotIncludeDeprecationHeadersForV2() throws Exception {
            mockMvc.perform(get("/api/v2/employees"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Deprecation"))
                    .andExpect(header().doesNotExist("Sunset"));
        }
    }

    @Nested
    class HeaderVersioning {

        @Test
        void shouldRouteToV2ByHeader() throws Exception {
            mockMvc.perform(get("/location").header("API-Version", "2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("v2")));
        }

        @Test
        void shouldDefaultToV2WhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/location"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("v2")));
        }

        @Test
        void shouldReturnBadRequestForUnsupportedHeaderVersion() throws Exception {
            mockMvc.perform(get("/location").header("API-Version", "3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unsupported API version"))
                    .andExpect(jsonPath("$.requestedVersion").value("3.0.0"))
                    .andExpect(jsonPath("$.currentVersion").value("2"));
        }

        @Test
        void shouldReturnBadRequestForUnparsableHeaderVersion() throws Exception {
            mockMvc.perform(get("/location").header("API-Version", "latest"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.requestedVersion").value("latest"));
        }

        @Test
        void shouldReturnNotFoundWhenSupportedVersionHasNoHandler() throws Exception {
            // v1 is supported globally, but /location only has a v2 handler
            mockMvc.perform(get("/location").header("API-Version", "1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class MediaTypeVersioning {

        @Test
        void shouldReturnProductV1WithMediaType() throws Exception {
            mockMvc.perform(get("/api/products").accept("application/vnd.company+json;v=1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Deprecation", V1_DEPRECATION))
                    .andExpect(jsonPath("$.data.name").value("Widget"))
                    .andExpect(jsonPath("$.data.price").value(29.99))
                    .andExpect(jsonPath("$.data.description").doesNotExist())
                    .andExpect(jsonPath("$.meta.apiVersion").value("v1"))
                    .andExpect(jsonPath("$.meta.deprecation").value("2025-12-31"));
        }

        @Test
        void shouldReturnProductV2WithMediaType() throws Exception {
            mockMvc.perform(get("/api/products").accept("application/vnd.company+json;v=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Widget"))
                    .andExpect(jsonPath("$.data.price").value(29.99))
                    .andExpect(jsonPath("$.data.description").value("Premium quality widget"))
                    .andExpect(jsonPath("$.data.sku").value("WIDGET-001"))
                    .andExpect(jsonPath("$.meta.apiVersion").value("v2"))
                    .andExpect(jsonPath("$.meta.deprecation").value(nullValue()));
        }

        @Test
        void shouldReturnBadRequestForUnsupportedMediaTypeVersion() throws Exception {
            mockMvc.perform(get("/api/products").accept("application/vnd.company+json;v=3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unsupported API version"))
                    .andExpect(jsonPath("$.documentation").value("/api/versions"));
        }
    }

    @Nested
    class QueryParameterVersioning {

        @Test
        void shouldReturnReportV1WithQueryVersion() throws Exception {
            mockMvc.perform(get("/api/reports?version=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Weekly Status"))
                    .andExpect(jsonPath("$.data.author").doesNotExist())
                    .andExpect(jsonPath("$.meta.apiVersion").value("v1"));
        }

        @Test
        void shouldDefaultToLatestReportVersion() throws Exception {
            mockMvc.perform(get("/api/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Weekly Status"))
                    .andExpect(jsonPath("$.data.author").value("Platform Team"))
                    .andExpect(jsonPath("$.meta.apiVersion").value("v2"));
        }

        @Test
        void shouldResolveSameEndpointFromHeader() throws Exception {
            // Resolvers are global: any configured strategy can select the version of any versioned endpoint
            mockMvc.perform(get("/api/reports").header("API-Version", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.author").doesNotExist())
                    .andExpect(jsonPath("$.meta.apiVersion").value("v1"));
        }

        @Test
        void shouldReturnBadRequestForUnsupportedQueryVersion() throws Exception {
            mockMvc.perform(get("/api/reports?version=3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unsupported API version"))
                    .andExpect(jsonPath("$.requestedVersion").value("3.0.0"))
                    .andExpect(jsonPath("$.supportedVersions", hasSize(2)));
        }
    }

    @Nested
    class JsonViewFieldEvolution {

        @Test
        void shouldHideV2FieldsFromV1View() throws Exception {
            mockMvc.perform(get("/api/employees/view?version=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").exists())
                    .andExpect(jsonPath("$.data[0].department").exists())
                    .andExpect(jsonPath("$.data[0].title").doesNotExist());
        }

        @Test
        void shouldExposeV2FieldsInV2View() throws Exception {
            mockMvc.perform(get("/api/employees/view?version=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").exists())
                    .andExpect(jsonPath("$.data[0].title").exists())
                    .andExpect(jsonPath("$.data[0].status").exists());
        }
    }

    @Nested
    class ETagBehavior {

        @Test
        void shouldReturnNotModifiedWhenIfNoneMatchMatches() throws Exception {
            MvcResult first = mockMvc.perform(get("/api/v2/employees/1"))
                    .andExpect(status().isOk())
                    .andReturn();

            String etag = first.getResponse().getHeader("ETag");
            mockMvc.perform(get("/api/v2/employees/1").header("If-None-Match", etag))
                    .andExpect(status().isNotModified());
        }

        @Test
        void shouldProduceDifferentEtagAcrossVersions() throws Exception {
            String v1Etag = mockMvc.perform(get("/v1/employees/1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader("ETag");

            String v2Etag = mockMvc.perform(get("/api/v2/employees/1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader("ETag");

            org.hamcrest.MatcherAssert.assertThat(v1Etag, not(v2Etag));
        }
    }

    @Nested
    class DiscoveryAndEnvelope {

        @Test
        void shouldReturnVersionDiscoveryInfo() throws Exception {
            mockMvc.perform(get("/api/versions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.current").value("v2"))
                    .andExpect(jsonPath("$.data.supported", hasSize(2)))
                    .andExpect(jsonPath("$.data.strategies", hasSize(4)))
                    .andExpect(jsonPath("$.meta.apiVersion").value("v2"));
        }

        @Test
        void shouldReturnEnvelopeForV2Employees() throws Exception {
            mockMvc.perform(get("/api/v2/employees"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.meta.apiVersion").value("v2"))
                    .andExpect(jsonPath("$.meta.timestamp").exists());
        }
    }

    @Nested
    class VersionContract {

        @ParameterizedTest
        @MethodSource("com.example.versioning.VersioningIntegrationTest#employeeVersionCases")
        void shouldServeExpectedFieldsForVersion(String path, String includedField, String excludedField) throws Exception {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(includedField).exists())
                    .andExpect(jsonPath(excludedField).doesNotExist());
        }

        @Test
        void shouldExposeVersionUsageEndpoint() throws Exception {
            mockMvc.perform(get("/api/v2/employees")).andExpect(status().isOk());
            mockMvc.perform(get("/v1/employees")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/api-versions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.v1.requests").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.v2.requests").value(greaterThanOrEqualTo(1)));
        }
    }

    static Stream<Arguments> employeeVersionCases() {
        return Stream.of(
                Arguments.of("/v1/employees", "$[0].department", "$[0].title"),
                Arguments.of("/api/v2/employees", "$.data[0].department", "$.data[0].unknownField")
        );
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("v1-disabled")
class V1DisabledProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDisableV1EndpointsWhenProfileDisablesV1() throws Exception {
        mockMvc.perform(get("/v1/employees"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectV1OnVersionedEndpoints() throws Exception {
        mockMvc.perform(get("/api/reports?version=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.supportedVersions", hasSize(1)));
    }

    @Test
    void versionDiscoveryShouldReflectDisabledV1() throws Exception {
        mockMvc.perform(get("/api/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supported", hasSize(1)));
    }
}
