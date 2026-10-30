ALTER TABLE subscriptions
    ALTER COLUMN activated_by_membership_id DROP NOT NULL,
    ADD COLUMN activated_by_user_id UUID;

ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_activation_actor_check CHECK (
        activated_by_membership_id IS NOT NULL OR activated_by_user_id IS NOT NULL
    );

COMMENT ON COLUMN subscriptions.activated_by_user_id IS
    'External identity-service UUID of a platform administrator who activated the subscription.';
