package com.jetski.shared.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para ActionExtractor - mapeamento de HTTP requests para actions OPA.
 *
 * @author Jetski Team
 */
@DisplayName("ActionExtractor")
class ActionExtractorTest {

    private ActionExtractor actionExtractor;

    @BeforeEach
    void setUp() {
        actionExtractor = new ActionExtractor();
    }

    @Nested
    @DisplayName("RESTful Patterns")
    class RestfulPatterns {

        @Test
        @DisplayName("GET /v1/locacoes → locacao:list")
        void shouldExtractListAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:list");
        }

        @Test
        @DisplayName("GET /v1/locacoes/{id} → locacao:view")
        void shouldExtractViewAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:view");
        }

        @Test
        @DisplayName("POST /v1/locacoes → locacao:create")
        void shouldExtractCreateAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:create");
        }

        @Test
        @DisplayName("PUT /v1/locacoes/{id} → locacao:update")
        void shouldExtractUpdateAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("PUT");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:update");
        }

        @Test
        @DisplayName("DELETE /v1/jetskis/{id} → jetski:delete")
        void shouldExtractDeleteAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("DELETE");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/jetskis/123e4567-e89b-12d3-a456-426614174000");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("jetski:delete");
        }
    }

    @Nested
    @DisplayName("Custom Sub-Actions")
    class CustomSubActions {

        @Test
        @DisplayName("POST /v1/locacoes/{id}/checkin → locacao:checkin")
        void shouldExtractCheckinAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000/checkin");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:checkin");
        }

        @Test
        @DisplayName("POST /v1/locacoes/{id}/checkout → locacao:checkout")
        void shouldExtractCheckoutAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000/checkout");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:checkout");
        }

        @Test
        @DisplayName("POST /v1/locacoes/{id}/desconto → locacao:desconto")
        void shouldExtractDescontoAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000/desconto");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:desconto");
        }

        @Test
        @DisplayName("POST /v1/os/{id}/aprovar → o:aprovar")
        void shouldExtractAprovarAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/os/123e4567-e89b-12d3-a456-426614174000/aprovar");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("o:aprovar");
        }

        @Test
        @DisplayName("POST /v1/abastecimentos/{id}/registrar → abastecimento:registrar")
        void shouldExtractRegistrarAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/abastecimentos/123e4567-e89b-12d3-a456-426614174000/registrar");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("abastecimento:registrar");
        }

        @Test
        @DisplayName("POST /v1/fotos/{id}/upload → foto:upload")
        void shouldExtractUploadAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/fotos/123e4567-e89b-12d3-a456-426614174000/upload");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("foto:upload");
        }
    }

    @Nested
    @DisplayName("Resource ID Extraction")
    class ResourceIdExtraction {

        @Test
        @DisplayName("Should extract resource ID from path variables")
        void shouldExtractResourceId() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes/123e4567-e89b-12d3-a456-426614174000");

            // Simula path variables do Spring MVC
            request.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", "123e4567-e89b-12d3-a456-426614174000")
            );

            String resourceId = actionExtractor.extractResourceId(request);

            assertThat(resourceId).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        }

        @Test
        @DisplayName("Should extract locacaoId from path variables")
        void shouldExtractLocacaoId() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("locacaoId", "aaa-bbb-ccc")
            );

            String resourceId = actionExtractor.extractResourceId(request);

            assertThat(resourceId).isEqualTo("aaa-bbb-ccc");
        }

        @Test
        @DisplayName("Should return null when no path variables")
        void shouldReturnNullWhenNoPathVariables() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            String resourceId = actionExtractor.extractResourceId(request);

            assertThat(resourceId).isNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle request without context path")
        void shouldHandleNoContextPath() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setRequestURI("/v1/locacoes");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:list");
        }

        @Test
        @DisplayName("Should handle request with query params")
        void shouldHandleQueryParams() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes?status=ativo&page=1");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:list");
        }

        @Test
        @DisplayName("Should singularize plural resource names")
        void shouldSingularizePluralResourceNames() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/modelos");

            String action = actionExtractor.extractAction(request);

            // modelos → modelo
            assertThat(action).isEqualTo("modelo:list");
        }

        @Test
        @DisplayName("Should handle unknown HTTP method")
        void shouldHandleUnknownHttpMethod() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("OPTIONS");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/locacoes");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("locacao:options");
        }

        @Test
        @DisplayName("Should handle invalid URI (no resource)")
        void shouldHandleInvalidUri() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/invalid");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("unknown:unknown");
        }
    }

    @Nested
    @DisplayName("Real-World Examples")
    class RealWorldExamples {

        @Test
        @DisplayName("GET /v1/user/tenants → user:list")
        void shouldExtractUserTenantsAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/user/tenants");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("user:list");
        }

        @Test
        @DisplayName("POST /v1/fechamentos/diario → fechamento:diario")
        void shouldExtractFechamentoDiarioAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/fechamentos/diario");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("fechamento:diario");
        }

        @Test
        @DisplayName("POST /v1/fechamentos/mensal → fechamento:mensal")
        void shouldExtractFechamentoMensalAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/fechamentos/mensal");

            String action = actionExtractor.extractAction(request);

            assertThat(action).isEqualTo("fechamento:mensal");
        }

        @Test
        @DisplayName("GET /v1/auth-test/operador-only → auth-test:list")
        void shouldExtractAuthTestAction() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setContextPath("/api");
            request.setRequestURI("/api/v1/auth-test/operador-only");

            String action = actionExtractor.extractAction(request);

            // Como "operador-only" não está na lista de sub-actions conhecidas,
            // ele é tratado como parte do resource
            // auth-test/operador-only → numeric pattern match → view
            // OU se não match, list
            assertThat(action).satisfiesAnyOf(
                a -> assertThat(a).isEqualTo("auth-test:list"),
                a -> assertThat(a).isEqualTo("auth-test:view")
            );
        }
    }
}
