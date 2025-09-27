package com.example.taskmanagement_backend.search.repositories;

import com.example.taskmanagement_backend.search.documents.TeamSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch repository for Team search operations
 */
@Repository
public interface TeamSearchRepository extends ElasticsearchRepository<TeamSearchDocument, String> {

    // Full-text search for teams
    @Query("{\"bool\": {\"should\": [{\"match\": {\"name\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\", \"boost\": 2.0}}}, {\"match\": {\"description\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}, {\"match\": {\"leaderName\": \"?0\"}}, {\"match\": {\"department\": \"?0\"}}], \"filter\": [{\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<TeamSearchDocument> findBySearchTerm(String searchTerm, Pageable pageable);

    // Autocomplete for team search
    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"name\": \"?0\"}}, {\"match_phrase_prefix\": {\"description\": \"?0\"}}, {\"match_phrase_prefix\": {\"department\": \"?0\"}}], \"filter\": [{\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<TeamSearchDocument> findByAutocomplete(String searchTerm, Pageable pageable);

    // Find teams by leader
    Page<TeamSearchDocument> findByLeaderId(Long leaderId, Pageable pageable);

    // Find teams by member
    Page<TeamSearchDocument> findByMemberIdsContains(Long memberId, Pageable pageable);

    // Find teams by type
    Page<TeamSearchDocument> findByTeamType(String teamType, Pageable pageable);

    // Find teams by department
    Page<TeamSearchDocument> findByDepartment(String department, Pageable pageable);

    // Find teams by privacy level
    Page<TeamSearchDocument> findByPrivacy(String privacy, Pageable pageable);

    // Find teams by tags
    Page<TeamSearchDocument> findByTagsIn(List<String> tags, Pageable pageable);

    // Find active teams with good performance
    @Query("{\"bool\": {\"must\": [{\"term\": {\"isActive\": true}}, {\"range\": {\"teamPerformanceScore\": {\"gte\": ?0}}}], \"filter\": [{\"term\": {\"searchable\": true}}]}}")
    Page<TeamSearchDocument> findHighPerformingTeams(Float minPerformanceScore, Pageable pageable);

    // Find teams user can join (not already a member)
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"description\", \"department\"]}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"must_not\": [{\"terms\": {\"memberIds\": [?1]}}]}}")
    Page<TeamSearchDocument> findJoinableTeams(String searchTerm, Long userId, Pageable pageable);

    // Complex search with filters
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"description\", \"department\"]}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"filter\": [{\"terms\": {\"teamType\": ?1}}, {\"terms\": {\"department\": ?2}}, {\"range\": {\"memberCount\": {\"gte\": ?3, \"lte\": ?4}}}]}}")
    Page<TeamSearchDocument> findBySearchTermWithFilters(
        String searchTerm,
        List<String> teamTypes,
        List<String> departments,
        Integer minMembers,
        Integer maxMembers,
        Pageable pageable
    );

    // Search with custom scoring based on performance and activity
    @Query("{\"function_score\": {\"query\": {\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"description\"]}}, \"functions\": [{\"field_value_factor\": {\"field\": \"teamPerformanceScore\", \"factor\": 1.2}}, {\"field_value_factor\": {\"field\": \"activeProjectsCount\", \"factor\": 0.1, \"modifier\": \"log1p\"}}], \"boost_mode\": \"multiply\"}}")
    Page<TeamSearchDocument> findWithCustomScoring(String searchTerm, Pageable pageable);
}
