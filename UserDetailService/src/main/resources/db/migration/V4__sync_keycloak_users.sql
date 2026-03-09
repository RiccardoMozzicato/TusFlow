-- Sincronizzazione utenti con Keycloak
-- Migration per allineare il database degli utenti con gli utenti presenti in Keycloak
-- Role: 0=ADMIN, 1=MANAGER, 2=STAFF

-- Correzione UUID di Samuele (era presente ma con UUID sbagliato)
-- Prima eliminiamo l'utente con UUID errato se esiste
DELETE FROM users WHERE email = 's456734@studenti.polito.it' AND id != '7fdcb70c-5b27-473f-ac44-97e37124ef64';

-- Inserimento/Aggiornamento Samuele con UUID corretto da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('7fdcb70c-5b27-473f-ac44-97e37124ef64', 'Samuele Scapellato', 's456734@studenti.polito.it', 'Politecnico di Torino', 1, 'Engineering', 5000, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    organization = EXCLUDED.organization,
    role = EXCLUDED.role,
    department = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at = NOW();

-- Inserimento Test Staff (mancante nel database)
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('2f7fd68e-e5b8-4055-920d-49fdb4f3d1d5', 'test staff', 'test_staff@keycloak.com', 'Politecnico di Torino', 2, 'Operations', 1000, NOW(), NOW())
ON CONFLICT (email) DO UPDATE SET
    id = EXCLUDED.id,
    full_name = EXCLUDED.full_name,
    organization = EXCLUDED.organization,
    role = EXCLUDED.role,
    department = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at = NOW();

-- Verifica e aggiornamento degli utenti esistenti per assicurare la coerenza
-- Andrea (Admin)
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('141f01bb-b503-4d05-81df-41e956059fe7', 'Andrea Di Prima', 's346392@studenti.polito.it', 'Politecnico di Torino', 0, 'HR', 0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    organization = EXCLUDED.organization,
    department = EXCLUDED.department,
    updated_at = NOW();

-- Riccardo (Admin/Staff)
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('a67d04bb-dac8-47de-90a2-e491588275aa', 'asd asd', 's347492@studenti.polito.it', 'Politecnico di Torino', 0, 'IT', 10000, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    organization = EXCLUDED.organization,
    department = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at = NOW();

-- Test Admin
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('a0727703-0653-449e-a4f3-039bf14ae73a', 'test admin', 's332244@studenti.polito.it', 'Politecnico di Torino', 0, 'IT', 434343, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    organization = EXCLUDED.organization,
    department = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at = NOW();

-- Test Manager
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('7444cf0b-efe9-4040-9a8a-c03a239c5b63', 'Test Manager', 's445522@studenti.polito.it', 'Politecnico di Torino', 1, 'HR', 23132131, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    organization = EXCLUDED.organization,
    department = EXCLUDED.department,
    storage_quota = EXCLUDED.storage_quota,
    updated_at = NOW();

