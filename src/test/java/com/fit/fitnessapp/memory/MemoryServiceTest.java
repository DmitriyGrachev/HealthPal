package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.memory.application.service.MemoryService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private VectorStore vectorStore;

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
    void recentContextSearchUsesServerSideUserIdFilter() {
        MemoryService service = serviceWithEmptyVectorResults();

        service.findRecentContext(108L, 7, 4);

        assertUserIdFilter(capturedSearchRequest(), 108L);
    }

    private MemoryService serviceWithEmptyVectorResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        return new MemoryService(vectorStore);
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
}
