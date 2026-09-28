package com.example.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.rag.model.AskResponse;
import com.example.rag.model.Source;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private static final int TOP_K = 4;
    private static final String SYSTEM_PROMPT = """
            You answer questions about uploaded documents.
            Use only the supplied context. If the answer is not present in the context,
            say clearly: \"I could not find that answer in the uploaded documents.\"
            Do not invent facts. Keep the answer concise and cite page numbers in natural language when useful.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public AskResponse ask(String question) {
        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question.trim())
                        .topK(TOP_K)
                        .build());
        if (matches == null) {
            matches = List.of();
        }

        String context = buildContext(matches);
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Context:\n" + context + "\n\nQuestion: " + question.trim())
                .call()
                .content();

        return new AskResponse(answer, extractSources(matches));
    }

    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "No relevant context was found in the uploaded documents.";
        }
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            context.append("[Chunk ").append(index + 1).append("] ")
                    .append(document.getText()).append("\n");
        }
        return context.toString();
    }

    private List<Source> extractSources(List<Document> documents) {
        Map<String, Source> uniqueSources = new LinkedHashMap<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String filename = String.valueOf(metadata.getOrDefault("filename", "unknown"));
            Integer page = toInteger(metadata.get("page_number"));
            String key = filename + ":" + page;
            uniqueSources.putIfAbsent(key, new Source(filename, page));
        }
        return new ArrayList<>(uniqueSources.values());
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.valueOf(value.toString());
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
