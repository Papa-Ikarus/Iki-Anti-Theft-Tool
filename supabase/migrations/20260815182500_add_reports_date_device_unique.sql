DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'reports_date_device_id_unique'
    ) THEN
        ALTER TABLE public.reports
        ADD CONSTRAINT reports_date_device_id_unique
        UNIQUE (date, device_id);
    END IF;
END
$$;
