package com.example.rag.model;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank(message = "question must not be empty")
        String question
) {
}
