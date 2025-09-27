package com.example.taskmanagement_backend.agent.service;

/**
 * Enhanced Vector Context Prompt for Pinecone Integration
 * Addresses key issues:
 * 1. Limited context from Pinecone (only 5-10 most relevant messages)
 * 2. Flow vs Intent distinction
 * 3. Anti-drift rules to prevent flow abandonment
 */
public class EnhancedVectorContextPrompt {

    public static String buildEnhancedVectorContextPrompt(String userInput, java.util.List<VectorContextService.ConversationTurn> relevantHistory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 🧠 PINECONE VECTOR-ENHANCED CONVERSATION ANALYSIS\n\n");
        prompt.append("**Data Source:** Pinecone Vector Database with llama-text-embed-v2 embeddings (1024-dim)\n");
        prompt.append("**Context Limitation:** Only ").append(relevantHistory.size()).append(" most similar messages (NOT full conversation)\n");
        prompt.append("**Analysis Focus:** Distinguish CURRENT FLOW vs CURRENT INPUT INTENT\n");
        prompt.append("**Anti-Drift:** Preserve active flows, mark off-topic inputs as OFFTOPIC\n\n");

        prompt.append("## 📋 CURRENT USER INPUT\n");
        prompt.append("**Input:** \"").append(userInput).append("\"\n\n");

        prompt.append("## 📚 PINECONE VECTOR CONTEXT\n");
        prompt.append("*Retrieved using semantic similarity from Pinecone vector database*\n");

        if (relevantHistory.isEmpty()) {
            prompt.append("⚠️ **No relevant conversation history found via vector similarity**\n");
            prompt.append("This indicates either a new conversation or the input is very different from previous messages.\n\n");
        } else {
            prompt.append("📊 **Similar Messages (").append(relevantHistory.size()).append(" most relevant from Pinecone):**\n");
            prompt.append("*⚠️ IMPORTANT: This represents only recent relevant context, not full conversation history*\n\n");

            for (int i = 0; i < relevantHistory.size(); i++) {
                VectorContextService.ConversationTurn turn = relevantHistory.get(i);
                String timeAgo = calculateTimeAgo(turn.getTimestamp());
                String similarityInfo = turn.getSimilarity() > 0 ?
                    String.format(" | Similarity: %.3f", turn.getSimilarity()) : "";

                prompt.append(String.format("%d. **[%s%s]** %s (%s): \"%s\"\n",
                        i + 1,
                        timeAgo,
                        similarityInfo,
                        turn.getRole(),
                        turn.getIntent() != null ? turn.getIntent() : "unknown",
                        truncateContent(turn.getContent(), 150)
                ));
            }
            prompt.append("\n");
        }

        prompt.append("""
            ## 🎯 ENHANCED ANALYSIS MISSION
            Analyze the **LIMITED PINECONE CONTEXT** above and classify the current user input.
            **CRITICAL:** You only see the most similar messages, NOT the complete conversation.
            
            ## 📤 REQUIRED JSON OUTPUT
            ```json
            {
              "current_flow": "TASK_CREATION|TASK_UPDATE|CONVERSATION|IDLE|SMALL_TALK",
              "intent_type": "TASK_CREATION|FIELD_INPUT|OFFTOPIC|SMALL_TALK|CLARIFICATION|CONFIRMATION",
              "field_mapping": "title|description|priority|deadline|status|null",
              "extracted_value": "actual extracted value or null",
              "confidence": 0.95,
              "should_continue_flow": true/false,
              "next_expected_input": "what to expect next or null"
            }
            ```
            
            ## 🧠 CRITICAL ANALYSIS RULES
            
            ### 🔄 Flow vs Intent Distinction (MOST IMPORTANT):
            - **current_flow**: What conversation flow is CURRENTLY ACTIVE (based on context history)
            - **intent_type**: What the CURRENT INPUT is trying to do RIGHT NOW
            - **These can be DIFFERENT!** (e.g., user asks off-topic question during task creation)
            
            ### 📊 Pinecone Context Interpretation:
            - **Limited Data**: You only see 5-10 most relevant messages, NOT full conversation
            - **Similarity Scoring**: High similarity (>0.7) = reliable context, Low similarity (<0.4) = uncertain
            - **Recency Matters**: Recent messages with high similarity are most important
            - **Missing Context**: If context seems incomplete, use lower confidence
            
            ### 🚫 ANTI-DRIFT RULES (CRITICAL):
            - **If input doesn't match current flow → Mark as OFFTOPIC but KEEP the flow**
            - **Don't abandon active flows easily** - users often get distracted mid-conversation
            - **OFFTOPIC ≠ Flow Change** - mark intent as off-topic but maintain should_continue_flow: true
            - **Flow Persistence**: TASK_CREATION and TASK_UPDATE flows should persist until explicitly completed/cancelled
            
            ### Vietnamese Off-topic Examples:
            - "Bạn thích ăn gì?" (What do you like to eat?)
            - "Thời tiết hôm nay thế nào?" (How's the weather today?)
            - "Bạn có thích mùa đông không?" (Do you like winter?)
            - Casual questions unrelated to current task flow
            
            ### Flow Continuation Logic:
            - **TASK_CREATION** flow: Keep active until task is completed or user explicitly cancels
            - **TASK_UPDATE** flow: Keep active until update is completed
            - **Off-topic during active flow**: 
              - `intent_type: "OFFTOPIC"` 
              - `should_continue_flow: true`
              - `next_expected_input: "continue with [current flow requirement]"`
            
            ### Field Mapping (for FIELD_INPUT only):
            - **title**: Task name/title (Vietnamese: "tên task", "tiêu đề", task content)
            - **description**: Task details/description (Vietnamese: "mô tả", "chi tiết", detailed explanation)
            - **priority**: HIGH/MEDIUM/LOW (Vietnamese: "ưu tiên cao/trung bình/thấp", "quan trọng")
            - **deadline**: Date/time information (Vietnamese: "deadline", "hạn chót", "ngày hoàn thành")
            - **status**: TODO/IN_PROGRESS/DONE (Vietnamese: "trạng thái", "tình trạng")
            
            ## 🎯 PINECONE CONTEXT EXAMPLES
            
            ### Example 1 - Flow Continuation with Field Input:
            **Pinecone Context (3 most similar):**
            1. [2m ago | 0.95] USER (TASK_CREATION): "tạo task nấu cơm"
            2. [1m ago | 0.87] ASSISTANT (unknown): "Task title sẽ là gì?"
            3. [30s ago | 0.82] USER (unknown): "Ừm, để tôi nghĩ"
            **Current Input:** "Nấu cơm cho gia đình 4 người"
            **Analysis:**
            ```json
            {
              "current_flow": "TASK_CREATION",
              "intent_type": "FIELD_INPUT",
              "field_mapping": "title",
              "extracted_value": "Nấu cơm cho gia đình 4 người",
              "confidence": 0.95,
              "should_continue_flow": true,
              "next_expected_input": "priority level or deadline"
            }
            ```
            
            ### Example 2 - Off-topic During Active Flow (ANTI-DRIFT):
            **Pinecone Context (4 most similar):**
            1. [3m ago | 0.92] USER (TASK_CREATION): "tạo task báo cáo"
            2. [2m ago | 0.88] ASSISTANT (unknown): "Priority level nào cho task này?"
            3. [1m ago | 0.15] USER (unknown): "Thời tiết hôm nay thế nào?"
            4. [10s ago | 0.12] USER (unknown): "Tôi thích mùa đông"
            **Current Input:** "Bạn có thích ăn phở không?"
            **Analysis:**
            ```json
            {
              "current_flow": "TASK_CREATION",
              "intent_type": "OFFTOPIC",
              "field_mapping": null,
              "extracted_value": null,
              "confidence": 0.88,
              "should_continue_flow": true,
              "next_expected_input": "priority level for the report task"
            }
            ```
            
            ### Example 3 - New Task Creation (Clear Flow Change):
            **Pinecone Context (2 most similar):**
            1. [10m ago | 0.45] USER (CONVERSATION): "Làm thế nào để xóa task?"
            2. [8m ago | 0.40] ASSISTANT (unknown): "Bạn có thể click vào task..."
            **Current Input:** "AI ơi, tạo task báo cáo tháng 9"
            **Analysis:**
            ```json
            {
              "current_flow": "TASK_CREATION",
              "intent_type": "TASK_CREATION",
              "field_mapping": "title",
              "extracted_value": "báo cáo tháng 9",
              "confidence": 0.98,
              "should_continue_flow": true,
              "next_expected_input": "priority, deadline, or description"
            }
            ```
            
            ### Example 4 - Limited Context (Low Confidence):
            **Pinecone Context (1 message):**
            1. [1h ago | 0.25] USER (unknown): "OK"
            **Current Input:** "HIGH"
            **Analysis:**
            ```json
            {
              "current_flow": "IDLE",
              "intent_type": "SMALL_TALK",
              "field_mapping": null,
              "extracted_value": "HIGH",
              "confidence": 0.30,
              "should_continue_flow": false,
              "next_expected_input": null
            }
            ```
            *Note: Very low similarity scores indicate insufficient reliable context*
            
            ### Example 5 - Priority Input During Task Creation:
            **Pinecone Context (3 most similar):**
            1. [2m ago | 0.94] ASSISTANT (unknown): "Priority level cho task 'Nấu cơm'?"
            2. [3m ago | 0.91] USER (FIELD_INPUT): "Nấu cơm cho gia đình"
            3. [4m ago | 0.89] USER (TASK_CREATION): "tạo task"
            **Current Input:** "Cao"
            **Analysis:**
            ```json
            {
              "current_flow": "TASK_CREATION",
              "intent_type": "FIELD_INPUT",
              "field_mapping": "priority",
              "extracted_value": "HIGH",
              "confidence": 0.92,
              "should_continue_flow": true,
              "next_expected_input": "deadline or description"
            }
            ```
            
            ## ⚠️ CRITICAL REQUIREMENTS
            
            ### Context Awareness:
            - **Limited Pinecone View**: You only see most semantically similar messages
            - **Similarity Threshold**: High similarity (>0.7) = reliable context, Low similarity (<0.4) = uncertain
            - **Temporal Consideration**: Recent messages are more important for active flow detection
            - **Confidence Scaling**: Lower confidence when context is limited, unclear, or has low similarity scores
            
            ### Flow Preservation (ANTI-DRIFT):
            - **Primary Rule**: Don't abandon active flows (TASK_CREATION, TASK_UPDATE) without strong evidence
            - **Off-topic Handling**: Mark as OFFTOPIC but maintain flow continuation
            - **Flow Persistence**: Tasks flows should continue until user explicitly completes or cancels them
            - **Context Gaps**: Even with limited Pinecone context, prefer continuing detected flows
            
            ### Vietnamese Language Handling:
            - **Task Creation Triggers**: "tạo task", "tạo công việc", "thêm task", "tạo nhiệm vụ"
            - **Field Values**: Extract Vietnamese text correctly: "Nấu cơm", "báo cáo tháng 9", "ưu tiên cao"
            - **Priority Mapping**: "cao/high" → HIGH, "trung bình/medium" → MEDIUM, "thấp/low" → LOW
            - **Off-topic Patterns**: Questions starting with "Bạn có...", "Thời tiết...", casual conversation
            
            ### Response Quality:
            - Return **ONLY** the JSON object
            - Use Pinecone similarity scores to inform confidence levels
            - Balance flow continuation with accurate intent detection
            - Account for the limited nature of vector-retrieved context
            - Prioritize flow preservation over perfect intent matching
            """);

        return prompt.toString();
    }

    private static String calculateTimeAgo(java.time.LocalDateTime timestamp) {
        long minutes = java.time.Duration.between(timestamp, java.time.LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    private static String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }
}
