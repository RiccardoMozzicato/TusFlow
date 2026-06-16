-- Allinea lo storage quota del DB con quello definito in Keycloak.
-- Nel realm Keycloak (keycloak/file-management-realm.json) ogni utente ha
-- l'attributo storage_quota = 1073741824 byte (= 1 GB binario, 1024^3).
-- Uniformiamo tutti gli utenti del DB allo stesso valore.
UPDATE users
SET storage_quota = 1073741824,
    updated_at    = NOW();
