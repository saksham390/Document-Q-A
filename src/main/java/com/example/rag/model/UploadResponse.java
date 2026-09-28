package com.example.rag.model;

public record UploadResponse(
        String documentId,
        String filename,
        int chunksStored,
        String message
) {
}
