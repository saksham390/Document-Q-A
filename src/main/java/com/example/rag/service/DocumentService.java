package com.example.rag.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.rag.exception.InvalidDocumentException;
import com.example.rag.model.UploadResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    public UploadResponse ingest(MultipartFile file) {
        validateFile(file);
        String filename = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString();

        try {
            byte[] pdfBytes = file.getBytes();
            if (!startsWithPdfSignature(pdfBytes)) {
                throw new InvalidDocumentException("The uploaded file is not a valid PDF");
            }

            ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    resource,
                    PdfDocumentReaderConfig.builder()
                            .withPagesPerDocument(1)
                            .build());

            List<Document> pages = reader.get();
            if (pages.isEmpty() || pages.stream().allMatch(document -> document.getText().isBlank())) {
                throw new InvalidDocumentException("The PDF does not contain readable text");
            }

            pages.forEach(page -> page.getMetadata().putAll(Map.of(
                    "document_id", documentId,
                    "filename", filename)));
            List<Document> chunks = textSplitter.apply(pages);
            if (chunks.isEmpty()) {
                throw new InvalidDocumentException("The PDF did not produce any searchable text chunks");
            }

            vectorStore.add(chunks);
            return new UploadResponse(documentId, filename, chunks.size(), "Document indexed successfully");
        }
        catch (InvalidDocumentException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new InvalidDocumentException("Could not read the uploaded PDF", exception);
        }
        catch (RuntimeException exception) {
            throw new InvalidDocumentException("Could not index the uploaded PDF", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Please upload a non-empty PDF file");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidDocumentException("Only PDF files are supported");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")) {
            throw new InvalidDocumentException("The uploaded file must have content type application/pdf");
        }
    }

    private boolean startsWithPdfSignature(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }
}
