package com.goldstack.order;

import com.goldstack.common.Money;
import com.goldstack.common.Side;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.order.Clients.PriceClient;
import com.goldstack.order.Clients.WalletClient;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.QuoteResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class QuoteService {

    private final QuoteStore quotes;
    private final PriceClient priceClient;
    private final WalletClient walletClient;
    private final GoldService goldService;
    private final OrderProperties props;
    private final Clock clock;

    public QuoteService(QuoteStore quotes, PriceClient priceClient, WalletClient walletClient, GoldService goldService,
                        OrderProperties props, Clock clock) {
        this.quotes = quotes;
        this.priceClient = priceClient;
        this.walletClient = walletClient;
        this.goldService = goldService;
        this.props = props;
        this.clock = clock;
    }

    public QuoteResponse createQuote(UUID userId, CreateQuoteRequest req) {
        BigDecimal price = priceClient.latest().pricePerGram();
        BigDecimal coins;
        BigDecimal grams;

        if (req.side() == Side.BUY) {
            if (req.coins() == null || req.grams() != null) {
                throw ApiException.badRequest("INVALID_QUOTE_REQUEST", "For BUY, send 'coins' only");
            }
            coins = Money.coins(req.coins());
            grams = Money.gramsForCoins(coins, price);

            if (walletClient.balance(userId).balance().compareTo(coins) < 0) {
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
                props.quoteValiditySeconds());
        quotes.save(quote);
        return QuoteResponse.from(quote);
    }
}
