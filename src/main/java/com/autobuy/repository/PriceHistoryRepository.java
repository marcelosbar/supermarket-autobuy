package com.autobuy.repository;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Spring Data Repository for PriceHistory entity.
 */
@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
	List<PriceHistory> findByProductOrderByRecordedAtDesc(Product product);
}
