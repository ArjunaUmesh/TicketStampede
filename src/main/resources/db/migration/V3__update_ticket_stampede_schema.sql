
ALTER TABLE ticket
DROP CONSTRAINT chk_ticket_status;

ALTER TABLE ticket
DROP CONSTRAINT chk_ticket_state;

ALTER TABLE ticket
    ADD CONSTRAINT chk_ticket_status
        CHECK (status IN ('AVAILABLE',
                          'SOLD',
                          'RESERVED'));

ALTER TABLE ticket
    ADD CONSTRAINT chk_ticket_state
        CHECK (
            (status = 'AVAILABLE'
                AND holder_user_id IS NULL
                AND sold_at IS NULL
                )
                OR
            (status = 'SOLD'
                AND holder_user_id IS NOT NULL
                AND sold_at IS NOT NULL
                )
                OR
            (status = 'RESERVED'
                AND holder_user_id IS NULL
                AND sold_at IS NULL
                )
            );

ALTER TABLE purchase_request
DROP CONSTRAINT chk_purchase_request_status;

ALTER TABLE purchase_request
DROP CONSTRAINT chk_purchase_request_state;

-- unique constraint on ticket can eb removed, as the ticket can be associated with another purchase request
ALTER TABLE purchase_request
DROP CONSTRAINT uk_purchase_request_ticket;

ALTER TABLE purchase_request
    ADD CONSTRAINT chk_purchase_request_status
        CHECK (status IN ('PROCESSING',
                          'PURCHASED',
                          'SOLD_OUT',
                          'RESERVED',
                          'RESERVATION_EXPIRED',
                          'PAYMENT_DECLINED',
                          'CANCELLED'));

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
            );

CREATE TABLE reservation
(
    id                  UUID PRIMARY KEY,
    purchase_request_id UUID        NOT NULL,
    ticket_id           UUID NOT NULL,
    user_id             TEXT        NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ NULL,

    CONSTRAINT fk_reservation_purchase_request
        FOREIGN KEY (purchase_request_id)
            REFERENCES purchase_request (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_reservation_ticket
        FOREIGN KEY (ticket_id)
            REFERENCES ticket (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_reservation_purchase_request_id
        UNIQUE (purchase_request_id),

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('ACTIVE',
                          'EXPIRED',
                          'CONFIRMED',
                          'CANCELLED')),

    CONSTRAINT chk_reservation_state
        CHECK (
            (status = 'ACTIVE'
                AND completed_at IS NULL)
                OR
            (status IN ('EXPIRED','CONFIRMED','CANCELLED')
                AND completed_at IS NOT NULL)
            ),

    CONSTRAINT chk_reservation_time_order
        CHECK (
            (expires_at > created_at
                )
                AND
            (
                completed_at IS NULL
                    OR
                completed_at >= created_at
                )
            )
);

-- Ensures that for one ticket is there's at most one active reservation
CREATE UNIQUE INDEX uk_reservation_one_active_per_ticket
    ON reservation (ticket_id)
    WHERE status = 'ACTIVE';


CREATE TABLE scheduled_task
(
    id                  UUID PRIMARY KEY,
    type                VARCHAR(32) NOT NULL,
    reference_id        UUID        NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    execute_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ NULL,

    CONSTRAINT chk_scheduled_task_status
    CHECK (status IN ('PENDING',
                      'PROCESSING',
                      'COMPLETED',
                      'FAILED',
                      'CANCELLED')),

    CONSTRAINT chk_scheduled_task_type
    CHECK (type IN ('RESERVATION_EXPIRY',
                    'QUEUE_ENTRY_EXPIRY')),

    CONSTRAINT chk_scheduled_task_state
        CHECK (
            (status IN ('PENDING', 'PROCESSING')
                AND completed_at IS NULL)
                OR
            (status IN ('COMPLETED', 'FAILED', 'CANCELLED')
                AND completed_at IS NOT NULL)
            ),

    CONSTRAINT chk_scheduled_task_time_order
        CHECK (
            execute_at >= created_at
                AND (
                completed_at IS NULL
                    OR completed_at >= created_at
                )
            )
);

-- Prevents duplicate scheduled tasks of the same type for the same reference
CREATE UNIQUE INDEX uk_scheduled_task_type_reference
    ON scheduled_task (type, reference_id);
