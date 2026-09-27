package com.mss.base.repository;

import com.mss.base.entity.MediaStorage;
import com.mss.base.enums.ReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@link MediaStorage} entities.
 */
@Repository
public interface MediaStorageRepository extends JpaRepository<MediaStorage, Integer>, QuerydslPredicateExecutor<MediaStorage> {
    Optional<MediaStorage> findByReferenceIdAndReferenceType(Integer referenceId, ReferenceType referenceType);
    List<MediaStorage> findAllByReferenceIdAndReferenceType(Integer referenceId, ReferenceType referenceType);
}