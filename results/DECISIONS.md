# Design Decisions

## 1. PostgreSQL as the Source of Truth

The service uses PostgreSQL as the authority for ticket inventory, purchase state, and concurrency control.

Each sale has its tickets created as individual rows in PostgreSQL. A ticket transitions from `AVAILABLE` to `SOLD`, and the database state determines whether inventory remains. The service does not maintain a separate mutable `soldCount`; 

`/status` derives the sold count from the persisted SOLD tickets.

Database constraints are also used as a second layer of protection for important invariants, including unique ticket numbers within a sale, globally unique request IDs, and one purchase request per issued ticket.

## 2. Concurrent Ticket Allocation

Ticket allocation uses:

```sql
SELECT ...
FROM ticket
WHERE sale_version_id = ?
  AND status = 'AVAILABLE'
ORDER BY ticket_number
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

`FOR UPDATE` locks the selected ticket until the transaction completes, preventing another transaction from selling the same ticket.

`SKIP LOCKED` allows concurrent buyers to skip tickets currently being processed by other transactions rather than waiting for the same row lock. This allows different purchase transactions to make progress against different tickets.

An issue is that an empty `SKIP LOCKED` result does not necessarily mean the sale is sold out. The remaining AVAILABLE tickets may simply be locked by other transactions. So one can't return asold out response in such a scenario

For this reason, when the allocation query returns no ticket,  normal existence check for an AVAILABLE ticket is done to determine sold out case

- No AVAILABLE ticket exists → the request is recorded as `SOLD_OUT`.
- An AVAILABLE ticket still exists → the empty result was caused by contention.

For contention, the service performs a small number of bounded retries with randomized jitter. If contention remains after the retry limit, the transaction is rolled back and a retryable `TICKET_CONTENTION` response is returned rather than recording `SOLD_OUT`.

## 3. Idempotent Purchase Requests

Clients provide a UUID `requestId` for each logical purchase attempt.

A new intended purchase uses a new request ID. If the client is uncertain whether a request completed, for example because the HTTP response was lost, it retries using the same request ID.

`requestId` has a database unique constraint and is treated as globally unique. A purchase request progresses through:

```text
PROCESSING -> PURCHASED
           -> SOLD_OUT
```

A terminal `PURCHASED` or `SOLD_OUT` result is replayed for subsequent requests using the same request ID.

If the same request ID is submitted with a different `userId`, the request is rejected rather than returning another user's result.

The initial `PROCESSING` record is claimed using an `INSERT ... ON CONFLICT DO NOTHING`. This allows PostgreSQL's unique constraint to arbitrate concurrent submissions of the same request ID rather than relying on a check-then-insert sequence in application code.

## 4. Transaction Boundary and Payment Failure

Ticket allocation, simulated payment, ticket state transition, and purchase completion occur within a single database transaction.

Once a ticket is selected with `FOR UPDATE`, its row lock is held while the simulated payment is performed.

On successful payment:

1. The ticket is changed from `AVAILABLE` to `SOLD`.
2. The purchase request is changed from `PROCESSING` to `PURCHASED`.
3. The transaction commits.

If payment fails, the transaction is rolled back. The ticket therefore remains AVAILABLE and the PROCESSING purchase request is not persisted. The client can retry the same logical request.

This keeps the implementation and failure semantics simple for the dummy in-process payment service. 
I would not use the same design for a real remote payment provider because holding a database lock across an external network operation could result in long-lived transactions. 
A production payment integration would instead require a reservation/payment workflow with explicit intermediate states and expiry handling.

Thus a state such as RESERVED may need to be inserted to indicate that payment is under processing

## 5. Sale Reset

Each `/reset` creates a new `SaleVersion` and a complete set of AVAILABLE ticket rows rather than deleting or reusing the previous sale.

Previous sale versions are retained, which keeps purchase history associated with the sale against which it occurred.

Concurrent resets are serialized using a PostgreSQL transaction-scoped advisory lock. The reset transaction:

1. Acquires the reset advisory lock.
2. Creates the new inactive sale and its all the tickets associated with it
3. Deactivates the previous active sale.
4. Activates the new sale.
5. Commits.

The advisory lock is automatically released when the transaction commits or rolls back.

The new sale is not exposed as active until its complete inventory has been created. This prevents buyers from observing a partially initialized sale.

A purchase that has already selected and bound itself to a sale is allowed to complete against that sale even if a concurrent reset subsequently makes another sale active. This avoids changing the meaning of a purchase after it has already begun.

## 6. Consistent Status

`/status` reads the SOLD tickets for the current active sale and derives `soldTicketCount` from that same list rather than reading a separately maintained counter.

The status operation uses a read-only `REPEATABLE_READ` transaction so the active sale and its sold-ticket snapshot remain consistent during the status request.

This also avoids synchronization problems between a mutable counter and the actual ticket records.

## 7. Database Constraints

Important correctness rules are enforced in both the application model and PostgreSQL where practical.

The schema includes constraints for:

- one active sale version at a time,
- unique `(sale_version_id, ticket_number)`,
- globally unique `requestId`,
- a ticket being associated with at most one purchase request,
- valid ticket state combinations,
- valid purchase-request state combinations,
- purchase requests and issued tickets belonging to the same sale.

The intention is that correctness should not depend solely on every application code path behaving correctly. Invalid persisted states should also be rejected by the database.

## 8. Performance Trade-offs

The initial schema did not contain an index specifically for AVAILABLE tickets. Performance investigation showed that PostgreSQL was locating tickets for a sale and filtering SOLD rows when checking for remaining inventory.

A partial index was therefore added:

```sql
CREATE INDEX idx_ticket_available_by_sale
    ON ticket (sale_version_id, ticket_number)
    WHERE status = 'AVAILABLE';
```

`EXPLAIN ANALYZE` confirmed that this reduced the database work required by the AVAILABLE-ticket queries and allowed the existence check to use an index-only scan.

However, controlled V1/V2 workload tests did not show a consistent end-to-end throughput improvement. With an inventory of only 100 tickets, this query was not the dominant system bottleneck.

I kept the index because it directly matches the access pattern and reduces unnecessary database work, but I do not treat the query-plan improvement as evidence of an application-level throughput improvement. The detailed measurements are documented in [`results/README.md`](results/README.md).

## 9. Scope

The implementation prioritizes the required single-datastore concurrency and failure cases while keeping the design small enough to reason about and test.

Some production concerns that can be incorporated : 

reservation expiry,\
distributed load generation,\
external payment orchestration,\
multi-region database behavior.
