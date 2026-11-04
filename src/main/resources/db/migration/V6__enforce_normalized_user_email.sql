DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        GROUP BY LOWER(BTRIM(email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Não foi possível normalizar e-mails duplicados na tabela users';
    END IF;
END $$;

UPDATE users
SET email = LOWER(BTRIM(email))
WHERE email <> LOWER(BTRIM(email));

CREATE UNIQUE INDEX uq_users_email_normalized ON users (LOWER(email));
