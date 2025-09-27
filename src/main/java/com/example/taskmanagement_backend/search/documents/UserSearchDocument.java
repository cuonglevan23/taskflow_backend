package com.example.taskmanagement_backend.search.documents;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document for User search indexing
 * Used for finding friends and team members
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "users")
public class UserSearchDocument {

    @Id
    private String id; // Keep as String for Elasticsearch compatibility

    @Field(type = FieldType.Long)
    private Long userId; // Add separate field for actual user ID

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String email;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String firstName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String lastName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String fullName; // firstName + lastName for easier searching

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String username;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String jobTitle;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String department;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String bio;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;

    // Professional information
    @Field(type = FieldType.Keyword)
    private List<String> skills;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String location;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String company;

    // Account status
    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Boolean)
    private Boolean isOnline;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime lastLoginAt;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;

    // Premium status
    @Field(type = FieldType.Boolean)
    private Boolean isPremium;

    @Field(type = FieldType.Keyword)
    private String premiumPlanType;

    // Privacy settings
    @Field(type = FieldType.Keyword)
    private String profileVisibility; // PUBLIC, FRIENDS_ONLY, PRIVATE

    @Field(type = FieldType.Boolean)
    private Boolean searchable; // Can be found in search

    // Friend relationships for filtering
    @Field(type = FieldType.Long)
    private List<Long> friendIds;

    // Team memberships
    @Field(type = FieldType.Long)
    private List<Long> teamIds;

    @Field(type = FieldType.Text, analyzer = "standard")
    private List<String> teamNames;

    // Search ranking factors
    @Field(type = FieldType.Float)
    private Float searchScore;

    @Field(type = FieldType.Integer)
    private Integer connectionsCount; // Number of friends/connections

    @Field(type = FieldType.Integer)
    private Integer completedTasksCount;
}
