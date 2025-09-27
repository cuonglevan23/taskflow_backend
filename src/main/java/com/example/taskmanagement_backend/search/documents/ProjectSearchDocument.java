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
 * Elasticsearch document for Project search indexing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "projects")
public class ProjectSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private LocalDateTime startDate;

    @Field(type = FieldType.Date)
    private LocalDateTime endDate;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    // Owner information
    @Field(type = FieldType.Long)
    private Long ownerId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String ownerName;

    // Team members for filtering
    @Field(type = FieldType.Long)
    private List<Long> memberIds;

    @Field(type = FieldType.Text, analyzer = "standard")
    private List<String> memberNames;

    // Task statistics
    @Field(type = FieldType.Integer)
    private Integer totalTasks;

    @Field(type = FieldType.Integer)
    private Integer completedTasks;

    @Field(type = FieldType.Float)
    private Float completionPercentage;

    // Privacy
    @Field(type = FieldType.Keyword)
    private String privacy;

    @Field(type = FieldType.Long)
    private List<Long> visibleToUserIds;

    // Search ranking
    @Field(type = FieldType.Float)
    private Float searchScore;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Keyword)
    private List<String> tags;
}
