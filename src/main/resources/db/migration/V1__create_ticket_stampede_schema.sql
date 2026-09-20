CREATE TABLE sale_version (
                              id UUID PRIMARY KEY,
                              capacity INTEGER NOT NULL,
                              started_at TIMESTAMPTZ NULL,
                              ended_at TIMESTAMPTZ NULL,
                              active BOOLEAN NOT NULL,

                              CONSTRAINT chk_sale_version_capacity
                                  CHECK (capacity > 0),

                              CONSTRAINT chk_sale_version_lifecycle
                                  CHECK (
                                      (active = FALSE AND started_at IS NULL AND ended_at IS NULL)
                                          OR
                                      (active = TRUE AND started_at IS NOT NULL AND ended_at IS NULL)
                                          OR
                                      (active = FALSE AND started_at IS NOT NULL AND ended_at IS NOT NULL)
                                      ),

                              CONSTRAINT chk_sale_version_time_order
                                  CHECK (
                                      ended_at IS NULL
                                          OR ended_at >= started_at
                                      )
);

CREATE UNIQUE INDEX uk_sale_version_one_active
    ON sale_version (active)
    WHERE active = TRUE;


CREATE TABLE ticket (
                        id UUID PRIMARY KEY,
                        sale_version_id UUID NOT NULL,
                        ticket_number INTEGER NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        holder_user_id TEXT NULL,
                        sold_at TIMESTAMPTZ NULL,
                        created_at TIMESTAMPTZ NOT NULL,

                        CONSTRAINT fk_ticket_sale_version
                            FOREIGN KEY (sale_version_id)
                                REFERENCES sale_version(id)
                                ON DELETE RESTRICT,

                        CONSTRAINT uk_ticket_sale_version_number
                            UNIQUE (sale_version_id, ticket_number),

                        CONSTRAINT uk_ticket_id_sale_version
                            UNIQUE (id, sale_version_id),

                        CONSTRAINT chk_ticket_number_positive
                            CHECK (ticket_number > 0),

                        CONSTRAINT chk_ticket_status
                            CHECK (status IN ('AVAILABLE', 'SOLD')),

                        CONSTRAINT chk_ticket_state
                            CHECK (
                                (status = 'AVAILABLE'
                                    AND holder_user_id IS NULL
                                    AND sold_at IS NULL)
                                    OR
                                (status = 'SOLD'
                                    AND holder_user_id IS NOT NULL
                                    AND sold_at IS NOT NULL)
                                )
);


CREATE TABLE purchase_request (
                                  id UUID PRIMARY KEY,
                                  request_id UUID NOT NULL,
                                  user_id TEXT NOT NULL,
                                  sale_version_id UUID NULL,
                                  ticket_id UUID NULL,
                                  status VARCHAR(16) NOT NULL,
                                  created_at TIMESTAMPTZ NOT NULL,
                                  completed_at TIMESTAMPTZ NULL,

                                  CONSTRAINT uk_purchase_request_request_id
                                      UNIQUE (request_id),

                                  CONSTRAINT uk_purchase_request_ticket
                                      UNIQUE (ticket_id),

                                  CONSTRAINT fk_purchase_request_sale_version
                                      FOREIGN KEY (sale_version_id)
                                          REFERENCES sale_version(id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_purchase_request_ticket_same_sale
                                      FOREIGN KEY (ticket_id, sale_version_id)
                                          REFERENCES ticket(id, sale_version_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT chk_purchase_request_status
                                      CHECK (status IN ('PROCESSING', 'PURCHASED', 'SOLD_OUT')),

                                  CONSTRAINT chk_purchase_request_state
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
                                          ),

                                  CONSTRAINT chk_purchase_request_time_order
                                      CHECK (
                                          completed_at IS NULL
                                              OR completed_at >= created_at
                                          )
);