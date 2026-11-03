package com.goldstack.order;

import com.goldstack.common.Money;
import com.goldstack.common.config.GoldStackProperties;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.QuoteResponse;
import com.goldstack.price.PriceService;
import com.goldstack.wallet.WalletService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class QuoteService {

    private final QuoteStore quotes;
    private final PriceService priceService;
    private final WalletService walletService;
    private final GoldService goldService;
    private final GoldStackProperties props;
    private final Clock clock;

    public QuoteService(QuoteStore quotes, PriceService priceService, WalletService walletService,
                        GoldService goldService, GoldStackProperties props, Clock clock) {
        this.quotes = quotes;
        this.priceService = priceService;
        this.walletService = walletService;
        this.goldService = goldService;
        this.props = props;
        this.clock = clock;
    }

    public QuoteResponse createQuote(UUID userId, CreateQuoteRequest req) {
        BigDecimal price = priceService.currentPrice().pricePerGram();
        BigDecimal coins;
        BigDecimal grams;

        if (req.side() == Side.BUY) {
            if (req.coins() == null || req.grams() != null) {
                throw ApiException.badRequest("INVALID_QUOTE_REQUEST", "For BUY, send 'coins' only");
            }
            coins = Money.coins(req.coins());
            grams = Money.gramsForCoins(coins, price);

            if (walletService.balanceOf(userId).compareTo(coins) < 0) {
                throw new InsufficientFundsException("INSUFFICIENT_BALANCE", "Not enough coins for this purchase");
            }
        } else {
            if (req.grams() == null || req.coins() != null) {
                throw ApiException.badRequest("INVALID_QUOTE_REQUEST", "For SELL, send 'grams' only");
            }
            grams = Money.grams(req.grams());
            coins = Money.payoutForGrams(grams, price);
            if (goldService.gramsOf(userId).compareTo(grams) < 0) {
                throw new InsufficientFundsException("INSUFFICIENT_GOLD", "You don't hold that much gold");
            }
        }
        if (grams.signum() == 0 || coins.signum() == 0) {
            throw ApiException.badRequest("AMOUNT_TOO_SMALL", "Amount is too small to trade at the current price");
        }

        Quote quote = Quote.create(userId, req.side(), price, coins, grams, Instant.now(clock),
                props.quote().validitySeconds());
        quotes.save(quote);
        return QuoteResponse.from(quote);
    }
}
