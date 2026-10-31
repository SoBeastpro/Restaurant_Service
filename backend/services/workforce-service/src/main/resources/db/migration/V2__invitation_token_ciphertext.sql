ALTER TABLE invitations
    ADD COLUMN token_ciphertext TEXT;

COMMENT ON COLUMN invitations.token_ciphertext IS
    'AES-GCM encrypted invitation token used to return a deep link to authorized managers.';
