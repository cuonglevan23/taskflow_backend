package com.example.taskmanagement_backend.search.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document for Task search indexing
 * Optimized for full-text search and multi-dimensional filtering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "tasks")
public class TaskSearchDocument {

    @Id
    private String id; // Task ID as string

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String priority;

    @Field(type = FieldType.Date)
    private LocalDateTime dueDate;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    // Assignee information for filtering
    @Field(type = FieldType.Long)
    private Long assigneeId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String assigneeName;

    @Field(type = FieldType.Keyword)
    private String assigneeEmail;

    // Creator information
    @Field(type = FieldType.Long)
    private Long creatorId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String creatorName;

    // Project information
    @Field(type = FieldType.Long)
    private Long projectId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String projectName;

    // Tags for filtering
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    // Privacy and permissions
    @Field(type = FieldType.Keyword)
    private String privacy; // PUBLIC, PRIVATE, TEAM

    @Field(type = FieldType.Long)
    private List<Long> visibleToUserIds; // For privacy filtering

    @Field(type = FieldType.Long)
    private List<Long> teamMemberIds; // For team task filtering

    // Completion status
    @Field(type = FieldType.Boolean)
    private Boolean isCompleted;

    @Field(type = FieldType.Integer)
    private Integer completionPercentage;

    // Search ranking factors
    @Field(type = FieldType.Float)
    private Float searchScore; // Custom scoring for ranking

    @Field(type = FieldType.Boolean)
    private Boolean isPinned;

    // File attachments count for relevance
    @Field(type = FieldType.Integer)
    private Integer attachmentCount;

    // Comments count for engagement scoring
    @Field(type = FieldType.Integer)
    private Integer commentCount;
}
