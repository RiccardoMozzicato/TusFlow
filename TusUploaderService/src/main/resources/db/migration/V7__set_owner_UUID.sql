ALTER TABLE uploads
DROP
COLUMN owner_id;

ALTER TABLE files
DROP
COLUMN owner_id;

ALTER TABLE files
    ADD owner_id UUID NOT NULL;

ALTER TABLE uploads
    ADD owner_id UUID NOT NULL;