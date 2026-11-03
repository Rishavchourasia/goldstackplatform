SELECT 'CREATE DATABASE ' || db
FROM unnest(ARRAY['users_db', 'wallet_db', 'price_db', 'order_db', 'sip_db', 'portfolio_db', 'notification_db']) AS db
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = db)
\gexec
