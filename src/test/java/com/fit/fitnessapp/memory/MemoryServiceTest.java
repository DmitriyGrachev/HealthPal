package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.memory.application.service.MemoryService;
import com.fit.fitnessapp.memory.domain.MemoryType;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private SensitiveAiEgressGuard egressGuard;

    @Test
    void deniedSensitiveEgressDoesNotReachVectorSearch() {
        doThrow(new IllegalStateException("denied")).when(egressGuard).validateSensitiveEgress();
        MemoryService service = new MemoryService(vectorStore, egressGuard);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.findRelevantMemories(42L, "private question", 5))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(vectorStore);
    }

    @Test
    void relevantMemorySearchUsesServerSideUserIdFilter() {
        MemoryService service = serviceWithEmptyVectorResults();

        service.findRelevantMemories(42L, "ignore user_id 99", 5);

        assertUserIdFilter(capturedSearchRequest(), 42L);
    }

    @Test
    void longTermFactsSearchUsesServerSideUserIdFilter() {
        MemoryService service = serviceWithEmptyVectorResults();

        service.findLongTermFacts(77L, 3);

        assertUserIdFilter(capturedSearchRequest(), 77L);
    }

    @Test
    void longTermFactsExcludeSemanticPeriodInsights() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("Monthly report summary", "SEMANTIC", "LONG_TERM"),
                document("User is allergic to peanuts", "FACT", "LONG_TERM")
        ));
        MemoryService service = new MemoryService(vectorStore, egressGuard);

        var facts = service.findLongTermFacts(77L, 3);

        assertThat(facts)
                .singleElement()
                .satisfies(memory -> {
                    assertThat(memory.content()).isEqualTo("User is allergic to peanuts");
                    assertThat(memory.type()).isEqualTo(MemoryType.FACT);
                });
    }

    @Test
    void recentContextSearchUsesServerSideUserIdFilter() {
        MemoryService service = serviceWithEmptyVectorResults();

        service.findRecentContext(108L, 7, 4);

        assertUserIdFilter(capturedSearchRequest(), 108L);
    }

    private MemoryService serviceWithEmptyVectorResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        return new MemoryService(vectorStore, egressGuard);
    }

    private SearchRequest capturedSearchRequest() {
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(vectorStore).similaritySearch(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private void assertUserIdFilter(SearchRequest request, Long expectedUserId) {
        assertThat(request.hasFilterExpression()).isTrue();

        Expression expression = request.getFilterExpression();
        assertThat(expression.type()).isEqualTo(ExpressionType.EQ);
        assertThat(expression.left()).isInstanceOf(Key.class);
        assertThat(((Key) expression.left()).key()).isEqualTo("user_id");
        assertThat(expression.right()).isInstanceOf(Value.class);
        assertThat(((Value) expression.right()).value()).isEqualTo(expectedUserId);
    }

    private Document document(String content, String memoryType, String memoryHorizon) {
        return new Document(
                UUID.randomUUID().toString(),
                content,
                Map.of(
                        "user_id", 77L,
                        "memory_type", memoryType,
                        "memory_horizon", memoryHorizon));
    }
}
