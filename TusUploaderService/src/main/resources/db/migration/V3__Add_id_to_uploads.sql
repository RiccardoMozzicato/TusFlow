-- Add auto-generated id column to uploads table and update primary key

-- Step 1: Rename uuid column to file_id
ALTER TABLE uploads RENAME COLUMN uuid TO file_id;

-- Step 2: Add the new id column (nullable for now)
ALTER TABLE uploads ADD COLUMN id UUID;

-- Step 3: Generate UUIDs for existing rows
UPDATE uploads SET id = gen_random_uuid() WHERE id IS NULL;

-- Step 4: Make id NOT NULL
ALTER TABLE uploads ALTER COLUMN id SET NOT NULL;

-- Step 5: Drop the old primary key constraint
ALTER TABLE uploads DROP CONSTRAINT pk_uploads;

-- Step 6: Add the new primary key on id
ALTER TABLE uploads ADD CONSTRAINT pk_uploads PRIMARY KEY (id);
