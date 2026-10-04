package com.example.graphql.specification;

import com.example.graphql.dto.filter.DateTimeFilter;
import com.example.graphql.dto.filter.UUIDFilter;
import com.example.graphql.dto.filter.VehicleFilter;
import com.example.graphql.dto.filter.VehicleTypeFilter;
import com.example.graphql.entity.Vehicle;
import com.example.graphql.enums.VehicleType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VehicleSpecification {

    private VehicleSpecification() {
    }

    public static Specification<Vehicle> fromFilter(VehicleFilter filter) {
        if (filter == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.type() != null) {
                addVehicleTypePredicates(predicates, cb, root.get("type"), filter.type());
            }

            if (filter.studentId() != null) {
                addUUIDPredicates(predicates, cb, root.get("student").get("id"), filter.studentId());
            }

            if (filter.createdAt() != null) {
                addDateTimePredicates(predicates, cb, root.get("createdAt"), filter.createdAt());
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addVehicleTypePredicates(List<Predicate> predicates, CriteriaBuilder cb,
                                                 Path<VehicleType> path, VehicleTypeFilter filter) {
        if (filter.eq() != null) {
            predicates.add(cb.equal(path, filter.eq()));
        }
        if (filter.in() != null && !filter.in().isEmpty()) {
            predicates.add(path.in(filter.in()));
        }
    }

    private static void addUUIDPredicates(List<Predicate> predicates, CriteriaBuilder cb,
                                          Path<UUID> path, UUIDFilter filter) {
        if (filter.eq() != null) {
            predicates.add(cb.equal(path, filter.eq()));
        }
        if (filter.in() != null && !filter.in().isEmpty()) {
            predicates.add(path.in(filter.in()));
        }
    }

    private static void addDateTimePredicates(List<Predicate> predicates, CriteriaBuilder cb,
                                              Path<OffsetDateTime> path, DateTimeFilter filter) {
        if (filter.eq() != null) {
            predicates.add(cb.equal(path, filter.eq()));
        }
        if (filter.gt() != null) {
            predicates.add(cb.greaterThan(path, filter.gt()));
        }
        if (filter.gte() != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, filter.gte()));
        }
        if (filter.lt() != null) {
            predicates.add(cb.lessThan(path, filter.lt()));
        }
        if (filter.lte() != null) {
            predicates.add(cb.lessThanOrEqualTo(path, filter.lte()));
        }
        if (filter.between() != null) {
            predicates.add(cb.between(path, filter.between().start(), filter.between().end()));
        }
    }
}
