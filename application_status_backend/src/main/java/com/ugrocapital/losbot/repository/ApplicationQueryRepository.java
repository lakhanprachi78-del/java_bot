package com.ugrocapital.losbot.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.stereotype.Repository;

import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.config.AppProperties;
import com.ugrocapital.losbot.entity.ApplicationApplicant;
import com.ugrocapital.losbot.entity.ApplicationApplicantEmail;
import com.ugrocapital.losbot.entity.LoanApplication;

@Repository
public class ApplicationQueryRepository {

    public static final int DEFAULT_RESULT_LIMIT = 5;
    private static final List<String> ANY_APPLICANT_TYPE = List.of("APPLICANT", "COAPPLICANT", "GUARANTOR");

    @PersistenceContext
    private EntityManager entityManager;

    private final ApplicationSerializer serializer;
    private final int maxRowsReturned;

    public ApplicationQueryRepository(ApplicationSerializer serializer, AppProperties properties) {
        this.serializer = serializer;
        this.maxRowsReturned = properties.app().maxRowsReturned();
    }

    public java.util.Optional<ApplicationDto> getApplicationById(String id, AuthContext auth) {
        List<ApplicationDto> results = execute(auth, (cb, root) -> cb.equal(root.get("applicationId"), id), 0, 1)
            .results();
        return results.stream().findFirst();
    }

    public SearchResult searchByApplicantName(String name, AuthContext auth, int limit) {
        return search(auth, 0, limit, (cb, root) -> applicantNamePredicate(cb, root, name));
    }

    public SearchResult searchByApplicantEmail(String email, AuthContext auth, int limit) {
        return search(auth, 0, limit, (cb, root) -> applicantEmailPredicate(cb, root, email));
    }

    public SearchResult searchByCreatedBy(String name, AuthContext auth, int offset, int limit) {
        return search(auth, offset, limit,
                (cb, root) -> cb.like(cb.lower(root.get("createdBy")), "%" + name.trim().toLowerCase() + "%"));
    }

    public SearchResult searchByDate(LocalDate date, AuthContext auth, int offset, int limit) {
        LocalDateTime start = date.atStartOfDay();
        return search(auth, offset, limit, (cb, root) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdDate"), start),
                cb.lessThan(root.get("createdDate"), start.plusDays(1))));
    }

    public SearchResult searchByDateRange(LocalDate start, LocalDate end, AuthContext auth, int offset, int limit) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime until = end.plusDays(1).atStartOfDay();
        return search(auth, offset, limit, (cb, root) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdDate"), from),
                cb.lessThan(root.get("createdDate"), until)));
    }

    public SearchResult searchByStatus(String statusCode, AuthContext auth, int offset, int limit) {
        return search(auth, offset, limit,
                (cb, root) -> cb.equal(cb.lower(root.get("statusCode")), statusCode.trim().toLowerCase()));
    }

    public SearchResult combinedSearch(CombinedSearchParams params, AuthContext auth) {
        return search(auth, params.offset(), params.limit(), (cb, root) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(params.applicationId())) {
                predicates.add(cb.equal(root.get("applicationId"), params.applicationId().trim()));
            }
            if (hasText(params.statusCode())) {
                predicates.add(cb.equal(cb.lower(root.get("statusCode")), params.statusCode().trim().toLowerCase()));
            }
            if (hasText(params.createdBy())) {
                predicates.add(cb.like(cb.lower(root.get("createdBy")), "%" + params.createdBy().trim().toLowerCase() + "%"));
            }
            if (hasText(params.applicantName())) {
                predicates.add(applicantNamePredicate(cb, root, params.applicantName()));
            }
            if (hasText(params.applicantEmail())) {
                predicates.add(applicantEmailPredicate(cb, root, params.applicantEmail()));
            }
            if (params.date() != null) {
                LocalDateTime from = params.date().atStartOfDay();
                predicates.add(cb.and(cb.greaterThanOrEqualTo(root.get("createdDate"), from),
                        cb.lessThan(root.get("createdDate"), from.plusDays(1))));
            } else if (params.startDate() != null && params.endDate() != null) {
                predicates.add(cb.and(cb.greaterThanOrEqualTo(root.get("createdDate"), params.startDate().atStartOfDay()),
                        cb.lessThan(root.get("createdDate"), params.endDate().plusDays(1).atStartOfDay())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    private SearchResult search(AuthContext auth, int offset, int requestedLimit, Filter filter) {
        return execute(auth, filter, Math.max(offset, 0), clampLimit(requestedLimit));
    }

    private SearchResult execute(AuthContext auth, Filter filter,
            int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<LoanApplication> query = cb.createQuery(LoanApplication.class);
        Root<LoanApplication> root = query.from(LoanApplication.class);
        List<Predicate> predicates = new ArrayList<>(List.of(filter.apply(cb, root)));
        addOwnerScope(cb, root, auth, predicates);
        query.select(root).where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(root.get("createdDate")));

        TypedQuery<LoanApplication> typedQuery = entityManager.createQuery(query)
                .setFirstResult(offset)
                .setMaxResults(limit + 1);
        List<LoanApplication> applications = typedQuery.getResultList();
        boolean hasMoreFromPage = applications.size() > limit;
        if (hasMoreFromPage) {
            applications = applications.subList(0, limit);
        }

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LoanApplication> countRoot = countQuery.from(LoanApplication.class);
        List<Predicate> countPredicates = new ArrayList<>(List.of(filter.apply(cb, countRoot)));
        addOwnerScope(cb, countRoot, auth, countPredicates);
        countQuery.select(cb.count(countRoot)).where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        boolean hasMore = hasMoreFromPage || total > offset + applications.size();

        return new SearchResult(applications.stream().map(serializer::serialize).toList(), total,
                applications.size(), hasMore);
    }

    private void addOwnerScope(CriteriaBuilder cb, Root<LoanApplication> root, AuthContext auth,
            List<Predicate> predicates) {
        if (!auth.isAdmin()) {
            String username = auth.databaseOwner().trim().toLowerCase();
            predicates.add(cb.or(
                    cb.equal(cb.lower(cb.trim(root.get("createdBy"))), username),
                    cb.equal(cb.lower(cb.trim(root.get("lastUpdatedBy"))), username)));
        }
    }

    private Predicate applicantNamePredicate(CriteriaBuilder cb, Root<LoanApplication> root, String name) {
        Subquery<Long> subquery = cb.createQuery(Long.class).subquery(Long.class);
        Root<ApplicationApplicant> applicant = subquery.from(ApplicationApplicant.class);
        subquery.select(cb.literal(1L)).where(
                cb.equal(applicant.get("application").get("applicationKey"), root.get("applicationKey")),
                cb.like(cb.lower(applicant.get("name")), "%" + name.trim().toLowerCase() + "%"),
                applicant.get("applicantType").in(ANY_APPLICANT_TYPE));
        return cb.exists(subquery);
    }

    private Predicate applicantEmailPredicate(CriteriaBuilder cb, Root<LoanApplication> root, String email) {
        Subquery<Long> subquery = cb.createQuery(Long.class).subquery(Long.class);
        Root<ApplicationApplicantEmail> applicantEmail = subquery.from(ApplicationApplicantEmail.class);
        subquery.select(cb.literal(1L)).where(
                cb.equal(applicantEmail.get("applicant").get("application").get("applicationKey"), root.get("applicationKey")),
                cb.like(cb.lower(applicantEmail.get("emailAddress")), "%" + email.trim().toLowerCase() + "%"),
                applicantEmail.get("applicant").get("applicantType").in(ANY_APPLICANT_TYPE));
        return cb.exists(subquery);
    }

    private int clampLimit(int requestedLimit) {
        int configuredMax = maxRowsReturned > 0 ? maxRowsReturned : DEFAULT_RESULT_LIMIT;
        return Math.max(1, Math.min(requestedLimit > 0 ? requestedLimit : DEFAULT_RESULT_LIMIT, configuredMax));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface Filter {
        Predicate apply(CriteriaBuilder builder, Root<LoanApplication> root);
    }
}