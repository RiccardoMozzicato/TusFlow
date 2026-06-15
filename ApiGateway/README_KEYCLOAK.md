# HOW TO CREATE ATTRIBUTE FOR USER IN KEYCLOAK

First of all
 1. Configure > Realm Settings > User Profile
 2. Create a new attribute, for example "employeeId"
 3. Set the attribute properties as needed (e.g., required, read-only, etc.)
 4. Who can view (User, Admin) who can edit (Admin)
 5. Validation should be provided
 6. Save the changes

Then
 1. Client scopes > profile (there are more pages, search for profile)
 2. Mappers > Add mapper, By configuration > User Attribute
 3. Choose the token that will show the attribute (For frontend usually its ID Token)

Last 
 1. Create a new user and fullfill the attribute created before
 2. Update existing user for the attribute created before
 3. Test by logging in and call /api/auth/userinfo and decode the ID JWT


# DEFAULT USERS

These users are seeded from `keycloak/file-management-realm.json` when the realm is imported.
All human users share the **same password** (the dev password set in the realm export; not stored in plaintext).
Every user has a storage quota of **1 GB (1073741824 bytes)**.

| Username        | Role    | Department | Organization           | Email                        |
|-----------------|---------|------------|------------------------|------------------------------|
| admin_IT        | admin   | IT         | Politecnico di Torino  | s346392@studenti.polito.it   |
| manager_IT      | manager | IT         | Politecnico di Torino  | s347492@studenti.polito.it   |
| staff_IT        | staff   | IT         | Politecnico di Torino  | s456734@studenti.polito.it   |
| manager_HR      | manager | HR         | Politecnico di Torino  | s500002@studenti.polito.it   |
| staff_HR        | staff   | HR         | Politecnico di Torino  | s500001@studenti.polito.it   |
| admin_Finance   | admin   | Finance    | Politecnico di Torino  | s500003@studenti.polito.it   |
| staff_Research  | staff   | Research   | Università di Bologna  | marco.rossi@unibo.it         |

### Service accounts (no password / storage quota)

| Username                     | Client     | Purpose                        |
|------------------------------|------------|--------------------------------|
| service-account-ai-client    | ai-client  | M2M client for the AI service  |
| service-account-tus-client   | tus-client | M2M client for the TUS uploader |
| service-account-user-admin   | user-admin | M2M client for user management |

# EXPORTING SETTINGS FROM KEYCLOAK

## This exports the file-management realm with users to a directory inside the container
docker exec -it lab04-g11-keycloak-1 /opt/keycloak/bin/kc.sh export --dir /opt/keycloak/data/import --realm file-management --users realm_file
## This exports the master realm 
docker exec -it lab04-g11-keycloak-1 /opt/keycloak/bin/kc.sh export --dir /opt/keycloak/data/import --realm master --users realm_file