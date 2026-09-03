package com.ugrocapital.appstatus.repository;

import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.config.AppProperties;
import com.ugrocapital.appstatus.dto.ApplicationDto;
import com.ugrocapital.appstatus.dto.SearchResult;
import com.ugrocapital.appstatus.model.ApplicationApplicant;
import com.ugrocapital.appstatus.model.ApplicationApplicantEmail;
import com.ugrocapital.appstatus.model.LoanApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository layer: the single choke point between the app and the
 * database. Direct port of repository.py.
 *
 * RULES THAT MUST NEVER BE BROKEN HERE (same as the Python original):
 *  1. No raw string-formatted SQL, ever. Only JPQL with bound (:param)
 *     parameters — dynamic WHERE fragments below are built from fixed,
 *     hardcoded clause text chosen by this code, never from user input;
 *     every actual VALUE always travels as a bound parameter.
 *  2. Every method takes typed, validated arguments — never a raw query string.
 *  3. Every method enforces MAX_ROWS_RETURNED.
 *  4. EVERY method takes an AuthContext and applies the ownership scope
 *     before running the query. There is no method here that returns
 *     data without going through the ownership check.
 *  5. Unauthorized lookups return "not found"/empty rather than an error,
 *     so we don't leak whether an application ID exists to someone who
 *     can't see it.
 */
@Repository
public class LoanApplicationRepository {

    private static final List<String> ANY_APPLICANT_TYPE = List.of("APPLICANT", "COAPPLICANT", "GUARANTOR");
    public static final int DEFAULT_RESULT_LIMIT = 5;

    private static final DateTimeFormatter DISPLAY_DT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    @PersistenceContext
    private EntityManager em;

    private final AppProperties appProperties;
    private final SensitiveDataRedactor redactor;

    public LoanApplicationRepository(AppProperties appProperties, SensitiveDataRedactor redactor) {
        this.appProperties = appProperties;
        this.redactor = redactor;
    }

    // --- Ownership scoping ---------------------------------------------
    // THE authorization enforcement point. Admins see everything;
    // everyone else only sees applications they created or currently own
    // (createdby == username OR lstupdatedby == username). Enforced below
    // inside buildWhere() so it's structurally impossible for any search
    // method to skip it.
    /**
     * Builds "WHERE <filters> AND (<ownership>)" safely — ownership is
     * always parenthesized so it never accidentally ORs across the rest
     * of the filter chain (a bug the naive string above would have).
     */
    private String buildWhere(List<String> filterClauses, AuthContext auth, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        List<String> all = new ArrayList<>(filterClauses);
        if (!"admin".equals(auth.role())) {
            params.put("normalizedUsername", auth.username().strip().toLowerCase());
            all.add("(LOWER(TRIM(a.createdBy)) = :normalizedUsername OR LOWER(TRIM(a.lastUpdatedBy)) = :normalizedUsername)");
        }
        if (all.isEmpty()) {
            return "";
        }
        sb.append(" WHERE ").append(String.join(" AND ", all));
        return sb.toString();
    }

    private void bind(TypedQuery<?> q, Map<String, Object> params) {
        params.forEach(q::setParameter);
    }

    // --- Core paginated search runner -----------------------------------

    private SearchResult runSearch(
            List<String> filterClauses,
            Map<String, Object> params,
            String orderBy,
            AuthContext auth,
            int offset,
            int limit
    ) {
        String where = buildWhere(filterClauses, auth, params);

        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(a) FROM LoanApplication a" + where, Long.class);
        bind(countQuery, params);
        long total = countQuery.getSingleResult();

        int cappedLimit = Math.min(limit, appProperties.getMaxRowsReturned());
        int safeOffset = Math.max(offset, 0);

        TypedQuery<LoanApplication> dataQuery = em.createQuery(
                "SELECT a FROM LoanApplication a" + where + " ORDER BY " + orderBy + " DESC",
                LoanApplication.class);
        bind(dataQuery, params);
        dataQuery.setFirstResult(safeOffset);
        dataQuery.setMaxResults(cappedLimit);
        List<LoanApplication> rows = dataQuery.getResultList();

        List<ApplicationDto> serialized = serializeAll(rows);
        boolean hasMore = (safeOffset + rows.size()) < total;

        return new SearchResult(serialized, total, serialized.size(), hasMore);
    }

    // --- Single lookup ---------------------------------------------------

    /** Exact lookup by application ID — returns null if not found OR not owned by this user. */
    public ApplicationDto getApplicationById(String applicationId, AuthContext auth) {
        Map<String, Object> params = new HashMap<>();
        params.put("applicationId", applicationId.strip().toUpperCase());
        List<String> filters = new ArrayList<>(List.of("UPPER(a.applicationId) = :applicationId"));

        String where = buildWhere(filters, auth, params);
        TypedQuery<LoanApplication> q = em.createQuery(
                "SELECT a FROM LoanApplication a" + where, LoanApplication.class);
        bind(q, params);
        List<LoanApplication> results = q.setMaxResults(1).getResultList();
        if (results.isEmpty()) {
            return null;
        }
        return serializeAll(results).get(0);
    }

    // --- Searches (each mirrors the identically-named repository.py function) ---

    public SearchResult searchByApplicantName(String name, AuthContext auth) {
        return searchByApplicantName(name, auth, DEFAULT_RESULT_LIMIT);
    }

    public SearchResult searchByApplicantName(String name, AuthContext auth, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("namePattern", "%" + name.strip().toLowerCase() + "%");
        params.put("applicantTypes", ANY_APPLICANT_TYPE);
        List<String> filters = List.of(
                "a.applicationKey IN (SELECT ap.applicationKey FROM ApplicationApplicant ap " +
                        "WHERE LOWER(ap.name) LIKE :namePattern AND ap.applicantType IN :applicantTypes)"
        );
        return runSearch(filters, params, "a.createdDt", auth, 0, limit);
    }

    public SearchResult searchByApplicantEmail(String email, AuthContext auth) {
        return searchByApplicantEmail(email, auth, DEFAULT_RESULT_LIMIT);
    }

    /** Find applications by an applicant's (or co-applicant's) email address. */
    public SearchResult searchByApplicantEmail(String email, AuthContext auth, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("emailPattern", "%" + email.strip().toLowerCase() + "%");
        params.put("applicantTypes", ANY_APPLICANT_TYPE);
        List<String> filters = List.of(
                "a.applicationKey IN (SELECT ap.applicationKey FROM ApplicationApplicant ap " +
                        "JOIN ApplicationApplicantEmail em ON em.appApplicantKey = ap.appApplicantKey " +
                        "WHERE LOWER(em.emailAddr) LIKE :emailPattern AND ap.applicantType IN :applicantTypes)"
        );
        return runSearch(filters, params, "a.createdDt", auth, 0, limit);
    }

    /** Case-insensitive partial match on createdBy, scoped to what this user may see.
     * Capped at DEFAULT_RESULT_LIMIT per call — returning MAX_ROWS_RETURNED every
     * time wastes tokens on records the model was only ever going to show 5 of anyway. */
    public SearchResult searchByCreatedBy(String name, AuthContext auth, int offset) {
        return searchByCreatedBy(name, auth, offset, DEFAULT_RESULT_LIMIT);
    }

    public SearchResult searchByCreatedBy(String name, AuthContext auth, int offset, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("createdByPattern", "%" + name.strip().toLowerCase() + "%");
        List<String> filters = List.of("LOWER(a.createdBy) LIKE :createdByPattern");
        return runSearch(filters, params, "a.createdDt", auth, offset, limit);
    }

    public SearchResult searchByDate(LocalDate targetDate, AuthContext auth, int offset) {
        return searchByDate(targetDate, auth, offset, DEFAULT_RESULT_LIMIT);
    }

    /** Applications created on an exact calendar date, scoped to this user. */
    public SearchResult searchByDate(LocalDate targetDate, AuthContext auth, int offset, int limit) {
        return searchByDateRange(targetDate, targetDate, auth, offset, limit);
    }

    public SearchResult searchByDateRange(LocalDate startDate, LocalDate endDate, AuthContext auth, int offset) {
        return searchByDateRange(startDate, endDate, auth, offset, DEFAULT_RESULT_LIMIT);
    }

    /** Applications created within an inclusive date range, scoped to this user. */
    public SearchResult searchByDateRange(LocalDate startDate, LocalDate endDate, AuthContext auth, int offset, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("startDt", LocalDateTime.of(startDate, LocalTime.MIN));
        params.put("endDt", LocalDateTime.of(endDate, LocalTime.MAX));
        List<String> filters = List.of("a.createdDt >= :startDt", "a.createdDt <= :endDt");
        return runSearch(filters, params, "a.createdDt", auth, offset, limit);
    }

    public SearchResult searchByStatus(String statusCode, AuthContext auth, int offset) {
        return searchByStatus(statusCode, auth, offset, DEFAULT_RESULT_LIMIT);
    }

    /** Applications matching a status code, scoped to this user. Supports
     * pagination via offset/limit so a follow-up "give me 5 more" can page
     * through results without repeating ones already shown. */
    public SearchResult searchByStatus(String statusCode, AuthContext auth, int offset, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("statusPattern", "%" + statusCode.strip().toLowerCase() + "%");
        List<String> filters = List.of("LOWER(a.statusCode) LIKE :statusPattern");
        return runSearch(filters, params, "a.lastUpdatedDt", auth, offset, limit);
    }

    /** Flexible multi-filter search, scoped to this user. Supports
     * pagination the same way as the single-filter searches above. */
    public SearchResult combinedSearch(
            AuthContext auth,
            String createdBy,
            String statusCode,
            LocalDate startDate,
            LocalDate endDate,
            String applicantName,
            String applicantEmail,
            int limit,
            int offset
    ) {
        Map<String, Object> params = new HashMap<>();
        List<String> filters = new ArrayList<>();

        if (createdBy != null && !createdBy.isBlank()) {
            params.put("createdByPattern", "%" + createdBy.strip().toLowerCase() + "%");
            filters.add("LOWER(a.createdBy) LIKE :createdByPattern");
        }
        if (statusCode != null && !statusCode.isBlank()) {
            params.put("statusExact", statusCode.strip().toLowerCase());
            filters.add("LOWER(a.statusCode) LIKE :statusExact");
        }
        if (applicantName != null && !applicantName.isBlank()) {
            params.put("namePattern", "%" + applicantName.strip().toLowerCase() + "%");
            params.put("applicantTypesForName", ANY_APPLICANT_TYPE);
            filters.add("a.applicationKey IN (SELECT ap.applicationKey FROM ApplicationApplicant ap " +
                    "WHERE LOWER(ap.name) LIKE :namePattern AND ap.applicantType IN :applicantTypesForName)");
        }
        if (applicantEmail != null && !applicantEmail.isBlank()) {
            params.put("emailPattern", "%" + applicantEmail.strip().toLowerCase() + "%");
            params.put("applicantTypesForEmail", ANY_APPLICANT_TYPE);
            filters.add("a.applicationKey IN (SELECT ap.applicationKey FROM ApplicationApplicant ap " +
                    "JOIN ApplicationApplicantEmail em ON em.appApplicantKey = ap.appApplicantKey " +
                    "WHERE LOWER(em.emailAddr) LIKE :emailPattern AND ap.applicantType IN :applicantTypesForEmail)");
        }
        if (startDate != null) {
            params.put("startDt", LocalDateTime.of(startDate, LocalTime.MIN));
            filters.add("a.createdDt >= :startDt");
        }
        if (endDate != null) {
            params.put("endDt", LocalDateTime.of(endDate, LocalTime.MAX));
            filters.add("a.createdDt <= :endDt");
        }

        return runSearch(filters, params, "a.createdDt", auth, offset, limit);
    }

    // --- Serialization (mirrors repository.py's _serialize) -------------

    private record ApplicantRow(Long applicationKey, Long appApplicantKey, String name, String applicantType) {
    }

    private List<ApplicationDto> serializeAll(List<LoanApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        List<Long> appKeys = apps.stream().map(LoanApplication::getApplicationKey).collect(Collectors.toList());

        // Batch-fetch every applicant row for these applications in one
        // query (no N+1), same for their emails — replicates the original's
        // eagerly-joined relationship loading without needing a mapped
        // JPA @OneToMany (which would otherwise make LIMIT/OFFSET pagination
        // unreliable due to the join's row multiplication).
        List<Object[]> applicantRows = em.createQuery(
                        "SELECT ap.applicationKey, ap.appApplicantKey, ap.name, ap.applicantType " +
                                "FROM ApplicationApplicant ap WHERE ap.applicationKey IN :keys",
                        Object[].class)
                .setParameter("keys", appKeys)
                .getResultList();

        List<ApplicantRow> applicants = applicantRows.stream()
                .map(r -> new ApplicantRow((Long) r[0], (Long) r[1], (String) r[2], (String) r[3]))
                .collect(Collectors.toList());

        List<Long> appApplicantKeys = applicants.stream().map(ApplicantRow::appApplicantKey).collect(Collectors.toList());

        Map<Long, String> emailByApplicantKey = new HashMap<>();
        if (!appApplicantKeys.isEmpty()) {
            List<Object[]> emailRows = em.createQuery(
                            "SELECT e.appApplicantKey, e.emailAddr FROM ApplicationApplicantEmail e " +
                                    "WHERE e.appApplicantKey IN :keys",
                            Object[].class)
                    .setParameter("keys", appApplicantKeys)
                    .getResultList();
            for (Object[] r : emailRows) {
                emailByApplicantKey.put((Long) r[0], (String) r[1]);
            }
        }

        Map<Long, List<ApplicantRow>> applicantsByAppKey = applicants.stream()
                .collect(Collectors.groupingBy(ApplicantRow::applicationKey));

        List<ApplicationDto> out = new ArrayList<>(apps.size());
        for (LoanApplication app : apps) {
            out.add(serialize(app, applicantsByAppKey.getOrDefault(app.getApplicationKey(), List.of()), emailByApplicantKey));
        }
        return out;
    }

    private ApplicationDto serialize(LoanApplication app, List<ApplicantRow> applicants, Map<Long, String> emailByApplicantKey) {
        List<String> applicantNames = applicants.stream()
                .filter(a -> a.name() != null && "APPLICANT".equalsIgnoreCase(trimOrEmpty(a.applicantType())))
                .map(ApplicantRow::name)
                .collect(Collectors.toList());

        List<String> coApplicantNames = applicants.stream()
                .filter(a -> a.name() != null && "COAPPLICANT".equalsIgnoreCase(trimOrEmpty(a.applicantType())))
                .map(ApplicantRow::name)
                .collect(Collectors.toList());

        List<String> applicantEmails = applicants.stream()
                .filter(a -> "APPLICANT".equalsIgnoreCase(trimOrEmpty(a.applicantType())))
                .map(a -> emailByApplicantKey.get(a.appApplicantKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ApplicationDto dto = new ApplicationDto();
        dto.applicationId = app.getApplicationId();
        dto.applicantName = applicantNames.isEmpty() ? null : String.join(", ", applicantNames);
        dto.coApplicantName = coApplicantNames.isEmpty() ? null : String.join(", ", coApplicantNames);
        dto.applicantEmail = applicantEmails.isEmpty() ? null : String.join(", ", applicantEmails);
        dto.status = app.getStatusCode();
        dto.createdBy = app.getCreatedBy();
        dto.createdAt = app.getCreatedDt() != null ? app.getCreatedDt().format(DISPLAY_DT_FORMAT) : null;
        dto.lastUpdatedBy = app.getLastUpdatedBy();
        dto.lastUpdatedAt = app.getLastUpdatedDt() != null ? app.getLastUpdatedDt().format(DISPLAY_DT_FORMAT) : null;
        dto.remark = redactor.redact(app.getRemarks());
        return dto;
    }

    private String trimOrEmpty(String s) {
        return s == null ? "" : s.strip();
    }
}
