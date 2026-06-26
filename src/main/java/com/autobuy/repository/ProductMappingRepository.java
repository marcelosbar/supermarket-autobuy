package com.autobuy.repository;

import com.autobuy.model.ProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Spring Data Repository for ProductMapping entity.
 */
@Repository
public interface ProductMappingRepository extends JpaRepository<ProductMapping, Long> {
	Optional<ProductMapping> findBySearchTextAndSupermarket(String searchText, String supermarket);
}
