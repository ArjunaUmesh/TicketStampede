CREATE INDEX idx_ticket_available_by_sale
    ON ticket (sale_version_id, ticket_number)
    WHERE status = 'AVAILABLE';