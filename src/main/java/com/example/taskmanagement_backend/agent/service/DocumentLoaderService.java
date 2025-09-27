package com.example.taskmanagement_backend.agent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Document Loader Service - Tự động tải và tích hợp tài liệu vào RAG khi khởi động
 * CHẠY TỰ ĐỘNG - Không cần gọi API, RAG sẽ có đầy đủ dữ liệu khi chat
 */
@Slf4j
@Service
public class DocumentLoaderService implements CommandLineRunner {

    private final RAGService ragService;

    public DocumentLoaderService(RAGService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 AUTO-LOADING ALL DOCS INTO RAG - Chat system will have full knowledge");

        // Load tất cả docs từ thư mục chính
        loadAllDocsFromMainDirectory();

        // Load từ agent docs nếu có
        loadFromAgentDocsDirectory();

        // Load comprehensive guides
        loadComprehensiveGuides();

        log.info("✅ RAG AUTO-LOADING COMPLETED - Chat system ready with full documentation knowledge!");
    }

    /**
     * Load tất cả docs từ thư mục docs/ chính
     */
    private void loadAllDocsFromMainDirectory() {
        try {
            Path docsPath = Paths.get("docs");

            if (!Files.exists(docsPath)) {
                log.warn("Main docs directory not found: docs/");
                return;
            }

            try (Stream<Path> paths = Files.walk(docsPath)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .forEach(this::autoLoadDocument);
            }

            log.info("📚 Loaded all documents from main docs directory");

        } catch (IOException e) {
            log.error("❌ Error auto-loading main docs", e);
        }
    }

    /**
     * Load từ agent docs directory
     */
    private void loadFromAgentDocsDirectory() {
        try {
            Path agentDocsPath = Paths.get("src/main/java/com/example/taskmanagement_backend/agent/docs");

            if (!Files.exists(agentDocsPath)) {
                log.debug("Agent docs directory not found - creating it for future use");
                Files.createDirectories(agentDocsPath);
                return;
            }

            try (Stream<Path> paths = Files.walk(agentDocsPath)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .forEach(this::autoLoadDocument);
            }

            log.info("📖 Loaded agent docs (including AI_AGENT_MYTASK_API_GUIDE.md, Taskflow_User_Guide.md if present)");

        } catch (IOException e) {
            log.error("❌ Error loading agent docs", e);
        }
    }

    /**
     * Auto-load một document vào RAG
     */
    private void autoLoadDocument(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String content = Files.readString(filePath);

            // Skip empty or too small files
            if (content.trim().length() < 50) {
                return;
            }

            log.debug("📄 Processing document: {} ({} chars)", fileName, content.length());

            // 1. Data Preprocessing: Chia document thành chunks
            List<DocumentChunk> chunks = preprocessDocument(fileName, content);

            // 2. Generate embeddings và 3. Upsert vào Pinecone cho từng chunk
            for (DocumentChunk chunk : chunks) {
                processDocumentChunk(chunk);
            }

            log.info("✅ Processed {} chunks from {}", chunks.size(), fileName);

        } catch (Exception e) {
            log.warn("⚠️ Failed to auto-load: {}", filePath, e);
        }
    }

    /**
     * 1. Data Preprocessing: Chia document thành chunks nhỏ với metadata
     */
    private List<DocumentChunk> preprocessDocument(String fileName, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Smart chunking based on content structure
        String[] sections = content.split("\\n\\s*#{1,3}\\s+"); // Split by headers

        if (sections.length <= 1) {
            // No headers found, split by paragraphs
            sections = content.split("\\n\\s*\\n");
        }

        int chunkIndex = 0;
        for (String section : sections) {
            if (section.trim().length() < 100) continue; // Skip too small chunks

            // Further split large sections
            List<String> subChunks = splitLargeSection(section.trim());

            for (String subChunk : subChunks) {
                if (subChunk.trim().length() < 50) continue;

                String chunkId = fileName.replaceAll("[^a-zA-Z0-9]", "_") + "_chunk_" + chunkIndex++;

                DocumentChunk chunk = DocumentChunk.builder()
                    .id(chunkId)
                    .content(subChunk.trim())
                    .sourceFile(fileName)
                    .chunkIndex(chunkIndex - 1)
                    .category(smartCategorize(fileName, content))
                    .metadata(createChunkMetadata(fileName, content, chunkIndex - 1))
                    .build();

                chunks.add(chunk);
            }
        }

        return chunks.isEmpty() ? List.of(createSingleChunk(fileName, content)) : chunks;
    }

    /**
     * Split large sections into smaller chunks (max ~500 chars)
     */
    private List<String> splitLargeSection(String section) {
        List<String> chunks = new ArrayList<>();

        if (section.length() <= 500) {
            chunks.add(section);
            return chunks;
        }

        // Split by sentences first
        String[] sentences = section.split("\\. ");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > 500 && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            currentChunk.append(sentence).append(". ");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * Create metadata for each chunk
     */
    private Map<String, Object> createChunkMetadata(String fileName, String content, int chunkIndex) {
        return Map.of(
            "fileName", fileName,
            "sourceType", "documentation",
            "category", smartCategorize(fileName, content),
            "chunkIndex", chunkIndex,
            "processedAt", LocalDateTime.now().toString(),
            "processingPipeline", "rag_v2"
        );
    }

    /**
     * Create single chunk for small documents
     */
    private DocumentChunk createSingleChunk(String fileName, String content) {
        String chunkId = fileName.replaceAll("[^a-zA-Z0-9]", "_") + "_single";

        return DocumentChunk.builder()
            .id(chunkId)
            .content(content.trim())
            .sourceFile(fileName)
            .chunkIndex(0)
            .category(smartCategorize(fileName, content))
            .metadata(createChunkMetadata(fileName, content, 0))
            .build();
    }

    /**
     * 2. Generate Embedding + 3. Upsert vào Pinecone cho từng chunk
     */
    private void processDocumentChunk(DocumentChunk chunk) {
        try {
            // 2. Generate embedding using llama-text-embed-v2
            double[] embedding = ragService.generateEmbeddingForChunk(chunk.getContent());

            // 3. Store in RAG (which will upsert to Pinecone)
            ragService.storeKnowledge(
                chunk.getId(),
                chunk.getSourceFile() + " - Chunk " + chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getMetadata()
            );

            log.debug("🔄 Processed chunk {} from {}", chunk.getChunkIndex(), chunk.getSourceFile());

        } catch (Exception e) {
            log.warn("❌ Failed to process chunk {} from {}: {}",
                chunk.getChunkIndex(), chunk.getSourceFile(), e.getMessage());
        }
    }

    /**
     * Smart categorization dựa trên tên file và nội dung
     */
    private String smartCategorize(String fileName, String content) {
        String lower = fileName.toLowerCase();
        String lowerContent = content.toLowerCase();

        // Task management related
        if (lower.contains("task") || lower.contains("mytask") || lowerContent.contains("task management")) {
            return "task_management";
        }

        // AI Agent related
        if (lower.contains("ai") || lower.contains("agent") || lowerContent.contains("ai agent")) {
            return "ai_agent";
        }

        // Chat system
        if (lower.contains("chat") || lowerContent.contains("chat system")) {
            return "chat_system";
        }

        // API documentation
        if (lower.contains("api") || lowerContent.contains("api endpoint")) {
            return "api_documentation";
        }

        // User guides
        if (lower.contains("guide") || lower.contains("user") || lowerContent.contains("how to")) {
            return "user_guide";
        }

        return "general";
    }

    /**
     * Load comprehensive system guides vào RAG
     */
    private void loadComprehensiveGuides() {
        // Task Management comprehensive guide
        ragService.storeKnowledge(
            "comprehensive_task_guide",
            "Complete Task Management System Guide",
            createTaskSystemGuide(),
            Map.of("type", "comprehensive", "category", "task_management", "priority", "high")
        );

        // AI Agent comprehensive guide
        ragService.storeKnowledge(
            "comprehensive_ai_agent_guide",
            "Complete AI Agent & Tool Calling Guide",
            createAIAgentGuide(),
            Map.of("type", "comprehensive", "category", "ai_agent", "priority", "high")
        );

        // Chat system guide
        ragService.storeKnowledge(
            "comprehensive_chat_guide",
            "Complete Chat System with RAG Guide",
            createChatSystemGuide(),
            Map.of("type", "comprehensive", "category", "chat_system", "priority", "high")
        );

        // Declining patterns guide
        ragService.storeKnowledge(
            "declining_handling_guide",
            "User Declining Intent Handling Guide",
            createDecliningGuide(),
            Map.of("type", "comprehensive", "category", "declining_patterns", "priority", "high")
        );

        log.info("🧠 Loaded comprehensive system guides into RAG");
    }

    /**
     * Tạo Task System Guide tổng hợp
     */
    private String createTaskSystemGuide() {
        return """
# Complete Task Management System Guide

## 🎯 Hệ thống Task Management với AI Chat
Hệ thống cho phép quản lý task hoàn toàn thông qua chat tự nhiên:

### Tạo Task:
- "Tạo task urgent hoàn thành báo cáo deadline ngày mai"
- "Create high priority task review presentation" 
- AI tự động extract: title, priority (HIGH/MEDIUM/LOW), deadline

### Xem Task:
- "Show my tasks", "Danh sách task của tôi"
- "High priority tasks", "Overdue tasks"
- Filter tự động theo status, priority, date

### Cập nhật Task:
- "Mark task 123 completed", "Update task title"
- "Change priority to high", "Set deadline Friday"

### Thống kê:
- "Task statistics", "How many completed?"
- "Progress this week", "Completion rate"

## 🚀 AI Features:
- Natural language processing (Tiếng Việt + English)
- Smart parameter extraction
- Context awareness from conversation
- Intelligent suggestions and error recovery

Task system hoạt động 100% thông qua chat - không cần UI phức tạp!
        """;
    }

    /**
     * Tạo AI Agent Guide tổng hợp
     */
    private String createAIAgentGuide() {
        return """
# Complete AI Agent & Tool Calling System Guide

## 🤖 AI Agent Core Functions:

### 1. Intent Detection:
- **ACTION**: User muốn làm gì → Call tools
- **QUERY**: User hỏi thông tin → Retrieve data  
- **CHAT**: Trò chuyện → RAG + LLM response
- **DECLINING**: User từ chối → Respect decision

### 2. Tool Calling:
- `createTask()`: Tạo task với smart parsing
- `getUserTasks()`: Lấy danh sách với filtering
- `getTaskStatistics()`: Thống kê chi tiết
- `updateTask()`: Cập nhật thông tin
- `deleteTask()`: Xóa với confirmation

### 3. Smart Processing:
- Auto parameter extraction từ natural language
- Context awareness từ conversation history  
- Error handling với helpful suggestions
- Multi-language support (VI + EN)

### 4. RAG Enhancement:
- Access tới full documentation knowledge
- Context-aware responses
- Learning từ user patterns
- Quality scoring cho relevance

## ⚡ Performance:
- Single unified AI call
- Smart caching và context reuse
- Sub-second response times
- Continuous learning và improvement

AI Agent thực sự "hiểu" và "làm việc" cho user, không chỉ trả lời!
        """;
    }

    /**
     * Tạo Chat System Guide tổng hợp
     */
    private String createChatSystemGuide() {
        return """
# Complete Chat System with RAG Enhancement

## 💬 Real-time Chat Features:
- WebSocket real-time communication
- Session persistence across conversations
- Message history và context continuity
- Multi-turn conversation support

## 🧠 RAG Integration:
- **Knowledge Base**: All docs auto-loaded
- **Smart Retrieval**: Vector similarity search
- **Context Enhancement**: Relevant info injected
- **Quality Scoring**: Best matches prioritized

## 🚀 AI Chat Capabilities:
- Natural language understanding
- Context-aware responses từ full documentation
- Pattern learning từ conversations  
- Declining detection và respectful handling

## 🔧 Technical Architecture:
- **CoreAgentService**: Main orchestrator
- **RAGService**: Context retrieval engine
- **UnifiedAIService**: Single point processing
- **SessionMemoryService**: Conversation persistence

## 💡 User Experience:
- Conversational interface - nói tự nhiên
- AI hiểu context và history
- Smart suggestions và proactive help
- Both Vietnamese và English support

Chat system tạo ra truly intelligent assistant!
        """;
    }

    /**
     * Tạo Declining Guide
     */
    private String createDecliningGuide() {
        return """
# User Declining Intent Handling Guide

## 🤝 Declining Detection System:

### Vietnamese Keywords:
- "không", "không cần", "không muốn" 
- "thôi", "bỏ qua", "từ chối"
- "hủy", "dừng lại", "thôi không"

### English Keywords:  
- "no", "no thanks", "don't want"
- "cancel", "stop", "decline"
- "refuse", "reject", "skip", "pass"

### Context-based Detection:
- Task-related declining: "không muốn tạo task"
- General declining: conversation context analysis
- Confidence scoring: 0.0 - 1.0

## 🎯 Smart Response Strategies:

### Task Decline:
"🤝 Hiểu rồi! Bạn không muốn tạo task lúc này.
💡 Đề xuất khác: ghi chú ý tưởng, reminder, thảo luận team"

### General Decline:
"🙏 Không sao cả! Tôi tôn trọng quyết định của bạn.
Nếu có gì khác cần giúp, đừng ngại nhé!"

### English Decline:
"👍 No problem at all! I completely understand.
Feel free to ask anything else - I'm here to help!"

## ⚡ System Behavior:
- **NO TOOL CALLING** when declining detected
- Respectful acknowledgment
- Alternative suggestions when appropriate
- Maintain helpful but not pushy tone

Declining system ensures AI respects user autonomy!
        """;
    }

    /**
     * Data class for document chunks
     */
    @Data
    @lombok.Builder
    private static class DocumentChunk {
        private String id;
        private String content;
        private String sourceFile;
        private int chunkIndex;
        private String category;
        private Map<String, Object> metadata;
    }
}
