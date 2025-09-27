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
 * Elasticsearch document for Team search indexing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "teams")
public class TeamSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    // Team leader information
    @Field(type = FieldType.Long)
    private Long leaderId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String leaderName;

    // Members
    @Field(type = FieldType.Long)
    private List<Long> memberIds;

    @Field(type = FieldType.Text, analyzer = "standard")
    private List<String> memberNames;

    @Field(type = FieldType.Integer)
    private Integer memberCount;

    // Team statistics
    @Field(type = FieldType.Integer)
    private Integer activeProjectsCount;

    @Field(type = FieldType.Integer)
    private Integer totalTasksCount;

    @Field(type = FieldType.Integer)
    private Integer completedTasksCount;

    @Field(type = FieldType.Float)
    private Float teamPerformanceScore;

    // Team status
    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Keyword)
    private String teamType; // DEVELOPMENT, MARKETING, DESIGN, etc.

    // Privacy
    @Field(type = FieldType.Keyword)
    private String privacy; // PUBLIC, PRIVATE, INVITE_ONLY

    @Field(type = FieldType.Boolean)
    private Boolean searchable;

    // Search ranking
    @Field(type = FieldType.Float)
    private Float searchScore;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    // Departments/Organizations
    @Field(type = FieldType.Text, analyzer = "standard")
    private String department;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String organization;
}
