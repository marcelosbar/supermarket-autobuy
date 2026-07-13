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
	java.util.List<ProductMapping> findBySearchTextAndSupermarketOrderByPriorityAsc(String searchText,
			String supermarket);

	Optional<ProductMapping> findBySupermarketAndExternalProductId(String supermarket, String externalProductId);
}
