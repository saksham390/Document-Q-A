# Document Q&A System using Spring AI and Pinecone

A small, beginner-friendly Retrieval-Augmented Generation (RAG) API. Upload PDF files, index their text in Pinecone, and ask questions that are answered using the most relevant document chunks.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer (or Maven Wrapper)
- OpenAI API key
- Pinecone account, API key, and an index

The configured OpenAI embedding model is `text-embedding-3-small`, which creates 1536-dimensional vectors. Create the Pinecone index with the same dimension and cosine metric. The chat model defaults to `gpt-4o-mini`; both model names can be changed with environment variables.

## Environment setup

PowerShell:

```powershell
$env:OPENAI_API_KEY="your-openai-key"
$env:PINECONE_API_KEY="your-pinecone-key"
$env:PINECONE_INDEX_NAME="your-index-name"
$env:PINECONE_HOST=""  # optional for serverless Pinecone setups
$env:PINECONE_ENVIRONMENT="us-east-1-aws"
$env:PINECONE_NAMESPACE="default"
$env:OPENAI_CHAT_MODEL="gpt-4o-mini"
$env:OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
```

Never commit these values. The application reads them from `application.properties` placeholders.

## Pinecone setup

1. Create a Pinecone project and API key.
2. Create an index named by `PINECONE_INDEX_NAME`.
3. Select cosine similarity and the dimension required by your embedding model. For `text-embedding-3-small`, use 1536.
4. Set the region/environment expected by your Pinecone account. New Pinecone serverless accounts primarily use an index host; if your Spring AI version requires one, add `spring.ai.vectorstore.pinecone.host=${PINECONE_HOST}` to `application.properties` and set `PINECONE_HOST`.
5. Start the application after setting the environment variables.

Spring AI's Pinecone property names can differ slightly between releases. This project targets Spring AI 1.0.0 and uses the `spring.ai.vectorstore.pinecone.*` properties shown in `application.properties`. Always compare them with the reference documentation for the exact Spring AI version selected in `pom.xml`.

## Run

```powershell
mvn spring-boot:run
```

Or build and run the jar:

```powershell
mvn clean package
java -jar target/document-qa-system-0.0.1-SNAPSHOT.jar
```

## API examples

### 1. Upload a PDF

Postman: `POST http://localhost:8081/api/documents/upload`

Body -> form-data:

| Key | Type | Value |
| --- | --- | --- |
| `file` | File | Choose a `.pdf` file |

Curl:

```bash
curl -X POST http://localhost:8081/api/documents/upload \
  -F "file=@spring-boot.pdf"
```

Example response:

```json
{
  "documentId": "9b5c...",
  "filename": "spring-boot.pdf",
  "chunksStored": 18,
  "message": "Document indexed successfully"
}
```

### 2. Ask a question

Postman: `POST http://localhost:8081/api/rag/ask`

Header: `Content-Type: application/json`

Body:

```json
{
  "question": "What is Spring Boot?"
}
```

Curl:

```bash
curl -X POST http://localhost:8081/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"What is Spring Boot?"}'
```

Example response:

```json
{
  "answer": "Spring Boot is ...",
  "sources": [
    { "document": "spring-boot.pdf", "page": 4 }
  ]
}
```

## Sample testing workflow

1. Download or create a small text-based PDF about Spring Boot.
2. Start Pinecone and the application with all environment variables set.
3. Upload the PDF and confirm that `chunksStored` is greater than zero.
4. Ask a question whose answer is explicitly in the PDF.
5. Ask an unrelated question. The answer should say that it could not find the answer in the uploaded documents.
6. Try an empty question, a `.txt` file, and an empty PDF to verify validation errors.

## Project structure

```text
src/main/java/com/example/rag/
├── controller/
│   ├── DocumentController.java
│   └── RagController.java
├── service/
│   ├── DocumentService.java
│   └── RagService.java
├── config/
│   └── VectorStoreConfig.java
├── model/
│   ├── AskRequest.java
│   ├── AskResponse.java
│   ├── Source.java
│   └── UploadResponse.java
└── exception/
    ├── GlobalExceptionHandler.java
    └── InvalidDocumentException.java
```

##  Spring AI 

- `PagePdfDocumentReader`: extracts PDF pages as Spring AI `Document` objects and retains page metadata.
- `TokenTextSplitter`: breaks large pages into smaller chunks so retrieval can find focused passages.
- `Document`: carries text plus metadata such as filename, page, and document ID.
- `VectorStore`: provider-neutral API used here for `add` and `similaritySearch`; Pinecone supplies the implementation through auto-configuration.
- `SearchRequest`: contains the query and `topK(4)` retrieval setting.
- `ChatClient`: sends the system instruction and retrieved context to the configured chat model.
- `ChatClient.Builder`: auto-configured by the OpenAI starter and used to create the application client.

## RAG 

RAG means Retrieval-Augmented Generation. Instead of asking the LLM to remember every document, the application first retrieves relevant passages and gives them to the LLM as context. The model then writes an answer grounded in those passages.

An embedding is a numeric representation of text. Similar meanings produce vectors that are close together. Pinecone is a managed vector database designed to store those vectors and search them quickly. Vector similarity search compares the question vector with stored chunk vectors and returns the closest matches.

Documents are chunked because sending an entire PDF for every question is expensive and makes relevant passages harder to locate. Top-K retrieval means keeping the best K matches; this implementation uses 4. The LLM receives those four chunks, which keeps the prompt focused.

RAG differs from fine-tuning: fine-tuning changes model behavior or style using training examples, while RAG supplies changing source knowledge at request time. RAG is usually the better first choice for private documents because the source can be updated without retraining the model.

Spring AI provides consistent Java abstractions for chat models, embedding models, document readers, splitters, and vector stores. That keeps application code small and makes it easier to change supported providers.

## Version note

Spring AI APIs and property names evolve. This project targets Spring AI `1.0.0`. In another release, the Pinecone starter artifact, property prefix, splitter builder methods, or `SearchRequest` builder may differ. Check the matching Spring AI reference documentation and update the small integration points rather than changing the overall architecture.
