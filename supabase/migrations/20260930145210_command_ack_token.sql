-- Rückmeldetoken gelten ausschließlich für den zugehörigen Befehl.
-- Bestehende Befehle bleiben ohne Token.

alter table public.commands
    add column ack_token_hash text,
    add column ack_token_expires_at timestamptz;

alter table public.commands
    add constraint commands_ack_token_valid
    check (
        (
            ack_token_hash is null
            and ack_token_expires_at is null
        )
        or (
            ack_token_hash is not null
            and ack_token_hash ~ '^[0-9a-f]{64}$'
            and ack_token_expires_at is not null
        )
    );

comment on column public.commands.ack_token_hash is
    'SHA-256-Hash des geheimen Rückmeldetokens; niemals den Klartext speichern.';

comment on column public.commands.ack_token_expires_at is
    'Serverseitiger Ablaufzeitpunkt des Rückmeldetokens.';