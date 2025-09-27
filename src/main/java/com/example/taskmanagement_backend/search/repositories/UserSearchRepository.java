package com.example.taskmanagement_backend.search.repositories;

import com.example.taskmanagement_backend.search.documents.UserSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch repository for User search operations
 * Used for finding friends, team members, and colleagues
 */
@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserSearchDocument, String> {

    // FIXED: Enhanced search that works with current email field mapping
    @Query("{\"bool\": {\"should\": [{\"match\": {\"email\": {\"query\": \"?0\", \"boost\": 5.0}}}, {\"match\": {\"fullName\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\", \"boost\": 2.0}}}, {\"match\": {\"username\": {\"query\": \"?0\", \"boost\": 1.5}}}, {\"match\": {\"firstName\": \"?0\"}}, {\"match\": {\"lastName\": \"?0\"}}, {\"match\": {\"jobTitle\": \"?0\"}}, {\"match\": {\"department\": \"?0\"}}], \"minimum_should_match\": 1}}")
    Page<UserSearchDocument> findBySearchTerm(String searchTerm, Pageable pageable);

    // Autocomplete for user search
    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"fullName\": \"?0\"}}, {\"match_phrase_prefix\": {\"email\": \"?0\"}}, {\"match_phrase_prefix\": {\"username\": \"?0\"}}]}}")
    Page<UserSearchDocument> findByAutocomplete(String searchTerm, Pageable pageable);

    // Search by email (exact match) - FIXED: Direct email search using match query instead of term
    @Query("{\"match\": {\"email\": \"?0\"}}")
    UserSearchDocument findByEmail(String email);

    // Search by username (exact match)
    UserSearchDocument findByUsername(String username);

    // Find users in same department
    @Query("{\"bool\": {\"must\": [{\"term\": {\"department\": \"?0\"}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"must_not\": [{\"term\": {\"id\": \"?1\"}}]}}")
    Page<UserSearchDocument> findByDepartment(String department, String excludeUserId, Pageable pageable);

    // Find users in same company
    @Query("{\"bool\": {\"must\": [{\"term\": {\"company\": \"?0\"}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"must_not\": [{\"term\": {\"id\": \"?1\"}}]}}")
    Page<UserSearchDocument> findByCompany(String company, String excludeUserId, Pageable pageable);

    // Find users by skills
    @Query("{\"bool\": {\"must\": [{\"terms\": {\"skills\": ?0}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<UserSearchDocument> findBySkills(List<String> skills, Pageable pageable);

    // Find premium users
    @Query("{\"bool\": {\"must\": [{\"term\": {\"isPremium\": true}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<UserSearchDocument> findPremiumUsers(Pageable pageable);

    // Find online users
    @Query("{\"bool\": {\"must\": [{\"term\": {\"isOnline\": true}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<UserSearchDocument> findOnlineUsers(Pageable pageable);

    // Find users by team membership
    @Query("{\"bool\": {\"must\": [{\"terms\": {\"teamIds\": [?0]}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}]}}")
    Page<UserSearchDocument> findByTeamId(Long teamId, Pageable pageable);

    // Advanced search with multiple filters
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"fullName^2\", \"email^1.5\", \"username^1.5\", \"jobTitle\", \"department\"]}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"filter\": [{\"terms\": {\"department\": ?1}}, {\"terms\": {\"skills\": ?2}}]}}")
    Page<UserSearchDocument> findBySearchTermWithFilters(String searchTerm, List<String> departments, List<String> skills, Pageable pageable);

    // Find potential friends (exclude current friends)
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"fullName^2\", \"email\", \"username\"]}}, {\"term\": {\"searchable\": true}}, {\"term\": {\"isActive\": true}}], \"must_not\": [{\"term\": {\"id\": \"?1\"}}, {\"terms\": {\"id\": ?2}}]}}")
    Page<UserSearchDocument> findPotentialFriends(String searchTerm, String currentUserId, List<String> friendIds, Pageable pageable);

    // FIXED: Simple search using match queries that work with current mapping
    @Query("{\"bool\": {\"should\": [{\"match\": {\"email\": {\"query\": \"?0\", \"boost\": 5.0}}}, {\"match\": {\"fullName\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\", \"boost\": 2.0}}}, {\"match\": {\"username\": {\"query\": \"?0\", \"boost\": 1.5}}}, {\"match\": {\"firstName\": \"?0\"}}, {\"match\": {\"lastName\": \"?0\"}}], \"minimum_should_match\": 1}}")
    Page<UserSearchDocument> findBySearchTermSimple(String searchTerm, Pageable pageable);

    // Search with custom scoring - fallback without filters
    @Query("{\"function_score\": {\"query\": {\"multi_match\": {\"query\": \"?0\", \"fields\": [\"fullName^2\", \"email^1.5\", \"username^1.5\"]}}, \"functions\": [{\"filter\": {\"match_all\": {}}, \"weight\": 1}]}}")
    Page<UserSearchDocument> findWithCustomScoring(String searchTerm, Pageable pageable);
}
