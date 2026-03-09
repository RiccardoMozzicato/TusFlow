-- Inserimento utenti iniziali sincronizzati con Keycloak
-- Nota: Gli UUID DEVONO corrispondere esattamente agli utenti in Keycloak
-- Role: 0=ADMIN, 1=MANAGER, 2=STAFF

-- Andrea (Admin) - UUID da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('141f01bb-b503-4d05-81df-41e956059fe7', 'Andrea Di Prima', 's346392@studenti.polito.it', 'Politecnico di Torino', 0, 'HR', 222, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Riccardo (Admin/Staff) - UUID da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('a67d04bb-dac8-47de-90a2-e491588275aa', 'asd asd', 's347492@studenti.polito.it', 'Politecnico di Torino', 0, 'IT', 10000, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Samuele (Staff) - UUID da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('9aabd908-683c-4791-bb20-defafd4dc1d3', 'Samuele Scapellato', 's456734@studenti.polito.it', 'Politecnico di Torino', 2, 'PR', 500, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Test Admin - UUID da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('a0727703-0653-449e-a4f3-039bf14ae73a', 'test admin', 's332244@studenti.polito.it', 'Politecnico di Torino', 0, 'IT', 434343, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Test Manager - UUID da Keycloak
INSERT INTO users (id, full_name, email, organization, role, department, storage_quota, created_at, updated_at)
VALUES
    ('7444cf0b-efe9-4040-9a8a-c03a239c5b63', 'Test Manager', 's445522@studenti.polito.it', 'Politecnico di Torino', 1, 'HR', 23132131, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

