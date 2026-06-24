-- Riallinea la tabella users con gli utenti attualmente presenti in Keycloak
-- (keycloak/file-management-realm.json). Gli UUID corrispondono agli id Keycloak.
-- Role: 0=ADMIN, 1=MANAGER, 2=STAFF. storage_quota in byte (1 GB).
-- storage_used e version usano i rispettivi DEFAULT (0) per i nuovi utenti.

-- 1. Rimuove gli utenti non più presenti in Keycloak (es. vecchi utenti di test).
DELETE FROM users
WHERE id NOT IN (
    '141f01bb-b503-4d05-81df-41e956059fe7', -- admin_IT
    'a67d04bb-dac8-47de-90a2-e491588275aa', -- manager_IT
    '7fdcb70c-5b27-473f-ac44-97e37124ef64', -- staff_IT
    'a0727703-0653-449e-a4f3-039bf14ae73a', -- staff_Research
    'f7d6bc67-8090-47cc-9baf-9309369fec2e', -- staff_HR
    'ea7167b5-2335-48c2-b8fb-7ce0f1ee2ffb', -- manager_HR
    '7560d30d-ae48-4a0d-bda5-abbfa2a08bdc'  -- admin_Finance
);

-- 2. Inserisce/aggiorna gli utenti correnti di Keycloak (match per UUID).
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('141f01bb-b503-4d05-81df-41e956059fe7', 'Andrea Di Prima',    's346392@studenti.polito.it', 'Politecnico di Torino', 0, 'IT',       1073741824, NOW(), NOW()),
    ('a67d04bb-dac8-47de-90a2-e491588275aa', 'asd asd',            's347492@studenti.polito.it', 'Politecnico di Torino', 1, 'IT',       1073741824, NOW(), NOW()),
    ('7fdcb70c-5b27-473f-ac44-97e37124ef64', 'Samuele Scapellato', 's456734@studenti.polito.it', 'Politecnico di Torino', 2, 'IT',       1073741824, NOW(), NOW()),
    ('a0727703-0653-449e-a4f3-039bf14ae73a', 'Marco Rossi',        'marco.rossi@unibo.it',       'Università di Bologna', 2, 'Research', 1073741824, NOW(), NOW()),
    ('f7d6bc67-8090-47cc-9baf-9309369fec2e', 'Giulia Bianchi',     's500001@studenti.polito.it', 'Politecnico di Torino', 2, 'HR',       1073741824, NOW(), NOW()),
    ('ea7167b5-2335-48c2-b8fb-7ce0f1ee2ffb', 'Luca Verdi',         's500002@studenti.polito.it', 'Politecnico di Torino', 1, 'HR',       1073741824, NOW(), NOW()),
    ('7560d30d-ae48-4a0d-bda5-abbfa2a08bdc', 'Elena Conti',        's500003@studenti.polito.it', 'Politecnico di Torino', 0, 'Finance',  1073741824, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name     = EXCLUDED.full_name,
    email         = EXCLUDED.email,
    organization  = EXCLUDED.organization,
    role          = EXCLUDED.role,
    department    = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at    = NOW();
