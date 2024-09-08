CREATE OR REPLACE FUNCTION notify_exchange_rate_update()
RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('exchange_rate_updated', '');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER exchange_rate_update_trigger
    AFTER INSERT OR UPDATE ON exchange_rate
                        FOR EACH ROW EXECUTE FUNCTION notify_exchange_rate_update();