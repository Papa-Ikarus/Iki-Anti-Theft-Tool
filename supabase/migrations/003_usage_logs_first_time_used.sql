-- Migration 003: first_time_used Spalte zu usage_logs hinzufügen
-- Ausführen in: Supabase Console → SQL Editor

ALTER TABLE usage_logs
ADD COLUMN IF NOT EXISTS first_time_used bigint;
