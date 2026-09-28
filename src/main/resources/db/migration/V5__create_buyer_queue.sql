CREATE TABLE buyer_queue
(
    id                  UUID PRIMARY KEY,
    user_id             VARCHAR(32) NOT NULL,
    purchase_request_id UUID        NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ NULL,

    CONSTRAINT fk_buyer_queue_purchase_request
        FOREIGN KEY (purchase_request_id)
            REFERENCES purchase_request(id)
            ON DELETE RESTRICT ,


    --Ensure that there is only one buyer queue entry for a purchase request
    CONSTRAINT uq_buyer_queue_purchase_request
        UNIQUE (purchase_request_id),

    CONSTRAINT chk_buyer_queue_status
        CHECK (status IN ('ACTIVE',
                          'FULFILLED',
                          'EXPIRED',
                          'CANCELLED')),

    CONSTRAINT chk_buyer_queue_state
        CHECK (
            (status = 'ACTIVE'
                AND completed_at IS NULL)
                OR
            (status IN ('FULFILLED', 'EXPIRED', 'CANCELLED')
                AND completed_at IS NOT NULL)
            ),

    CONSTRAINT chk_buyer_queue_time_order
        CHECK (
            expires_at >= created_at
                AND (
                completed_at IS NULL
                    OR completed_at >= created_at
                )
            )
);

ALTER TABLE purchase_request
DROP CONSTRAINT chk_purchase_request_status;

ALTER TABLE purchase_request
DROP CONSTRAINT chk_purchase_request_state;

ALTER TABLE purchase_request
    ADD CONSTRAINT chk_purchase_request_status
        CHECK (status IN ('PROCESSING',
                          'PURCHASED',
                          'SOLD_OUT',
                          'RESERVED',
                          'RESERVATION_EXPIRED',
                          'PAYMENT_DECLINED',
                          'CANCELLED',
                          'QUEUED',
                          'QUEUE_EXPIRED'));

ALTER TABLE purchase_request
    ADD CONSTRAINT chk_purchase_request_state
        CHECK (
            (status = 'PROCESSING'
                AND ticket_id IS NULL
                AND completed_at IS NULL)
                OR
            (status = 'PURCHASED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NOT NULL
                AND completed_at IS NOT NULL)
                OR
            (status = 'SOLD_OUT'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NULL
                AND completed_at IS NOT NULL)
                OR
            (status = 'RESERVED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NOT NULL
                AND completed_at IS NULL)
                OR
            (status = 'RESERVATION_EXPIRED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NOT NULL
                AND completed_at IS NOT NULL)
                OR
            (status = 'PAYMENT_DECLINED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NOT NULL
                AND completed_at IS NOT NULL)
                OR
            (status = 'CANCELLED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NOT NULL
                AND completed_at IS NOT NULL)
                OR
            (status = 'QUEUED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NULL
                AND completed_at IS NULL)
                OR
            (status = 'QUEUE_EXPIRED'
                AND sale_version_id IS NOT NULL
                AND ticket_id IS NULL
                AND completed_at IS NOT NULL)
            );