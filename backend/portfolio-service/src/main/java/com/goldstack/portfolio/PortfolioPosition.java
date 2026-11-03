package com.goldstack.portfolio;

import com.goldstack.common.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "portfolio_positions")
class PortfolioPosition {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_grams", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalGrams;

    @Column(name = "total_invested", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalInvested;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PortfolioPosition() {
    }

    PortfolioPosition(UUID userId, Instant now) {
        AverageCost empty = AverageCost.empty();
        this.userId = userId;
        this.totalGrams = empty.grams();
        this.totalInvested = empty.invested();
        this.updatedAt = now;
    }

    void apply(Side side, BigDecimal grams, BigDecimal coins, Instant now) {
        AverageCost next = new AverageCost(totalGrams, totalInvested).apply(side, grams, coins);
        this.totalGrams = next.grams();
        this.totalInvested = next.invested();
        this.updatedAt = now;
    }

    BigDecimal getTotalGrams() {
        return totalGrams;
    }

    BigDecimal getTotalInvested() {
        return totalInvested;
    }
}

interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PortfolioPosition p where p.userId = :userId")
    Optional<PortfolioPosition> lockByUserId(@Param("userId") UUID userId);
}
