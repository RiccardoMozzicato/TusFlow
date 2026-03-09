## EXPORT FILE-MANAGEMENT REALM
docker exec -it lab05-g11-keycloak-1 /opt/keycloak/bin/kc.sh export --dir /opt/keycloak/data/import --realm file-management --users realm_file