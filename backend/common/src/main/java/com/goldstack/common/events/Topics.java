package com.goldstack.common.events;

import java.util.List;

public final class Topics {

    public static final String PRICE_UPDATED = "gold.price.updated";

    public static final String USER_REGISTERED = "user.registered";

    public static final String ORDER_PLACED = "order.placed";

    public static final String WALLET_SETTLED = "wallet.settled";

    public static final String WALLET_SETTLEMENT_FAILED = "wallet.settlement.failed";

    public static final String ORDER_COMPLETED = "order.completed";

    public static final String ORDER_FAILED = "order.failed";

    public static final String SIP_DEBIT_REQUESTED = "sip.debit.requested";

    public static final String SIP_DEBIT_FAILED = "sip.debit.failed";

    public static final String NOTIFICATION_SEND = "notification.send";

    public static final List<String> ALL = List.of(PRICE_UPDATED, USER_REGISTERED, ORDER_PLACED, WALLET_SETTLED,
            WALLET_SETTLEMENT_FAILED, ORDER_COMPLETED, ORDER_FAILED, SIP_DEBIT_REQUESTED, SIP_DEBIT_FAILED,
            NOTIFICATION_SEND);

    private Topics() {
    }
}
