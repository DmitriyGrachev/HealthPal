DELETE FROM user_notes note
WHERE NOT EXISTS (
    SELECT 1
    FROM users app_user
    WHERE app_user.id = note.user_id
);

ALTER TABLE user_notes
    ADD CONSTRAINT fk_user_notes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE;
