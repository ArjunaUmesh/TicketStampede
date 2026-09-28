# Design Decisions

## 1. PostgreSQL as the Source of Truth

The service uses PostgreSQL as the authority for ticket inventory, purchase requests, reservations, buyer-queue state, sale versions, and concurrency control.

Each sale has its tickets represented as individual rows. A ticket progresses through:

```text
AVAILABLE → RESERVED → SOLD
```

`RESERVED` represents temporary ownership while a buyer completes payment. Permanent ownership is recorded only when the reservation is confirmed and the ticket transitions to `SOLD`.

The service does not maintain a separate mutable `soldCount`. `/status` derives sold inventory from persisted ticket state.

Database constraints are also used as a second layer of protection for important invariants rather than relying entirely on application code.

---

## 2. Concurrent Ticket Allocation

Ticket allocation uses PostgreSQL row locking:

```sql
SELECT *
FROM ticket
WHERE sale_version_id = ?
  AND status = 'AVAILABLE'
ORDER BY ticket_number ASC
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

`FOR UPDATE` locks the selected ticket until the transaction completes, preventing another transaction from simultaneously reserving the same ticket.

`SKIP LOCKED` allows concurrent buyers to skip rows currently locked by other transactions rather than waiting for the same ticket. This allows different purchase transactions to make progress against different ticket rows.

An important consequence is that an empty `SKIP LOCKED` result does not necessarily mean that the sale is sold out. An `AVAILABLE` ticket may exist but currently be locked by another transaction.

The service therefore distinguishes three cases:

```text
Acquirable AVAILABLE ticket
        |
        v
     RESERVED

No acquirable ticket,
but AVAILABLE or RESERVED tickets exist
        |
        v
      QUEUED

No AVAILABLE or RESERVED tickets
        |
        v
     SOLD_OUT
```

The current implementation intentionally does not perform bounded retries or randomized jitter when an available ticket is temporarily inaccessible because of contention.

This means strict arrival fairness is not guaranteed. For example, one buyer may be queued because an available ticket is temporarily locked while a later buyer may acquire that ticket after the lock is released.

This trade-off keeps the allocation path simple while preserving the more important correctness property that the same ticket is not simultaneously allocated to multiple buyers.

---

## 3. Idempotent Purchase Requests

Clients provide a UUID `requestId` for each logical purchase attempt.

A new intended purchase uses a new request ID. If the client is uncertain whether a request completed, for example because an HTTP response was lost, it retries using the same request ID.

`requestId` has a database uniqueness constraint and is treated as globally unique.

A purchase request can progress through states including:

```text
PROCESSING
RESERVED
QUEUED
PURCHASED
SOLD_OUT
RESERVATION_EXPIRED
PAYMENT_DECLINED
CANCELLED
QUEUE_EXPIRED
```

Typical immediate-reservation flow:

```text
PROCESSING → RESERVED → PURCHASED
```

Queued flow:

```text
PROCESSING → QUEUED → RESERVED → PURCHASED
```

Expiry flows include:

```text
RESERVED → RESERVATION_EXPIRED
```

and:

```text
QUEUED → QUEUE_EXPIRED
```

If the same `requestId` is submitted again by the same user, the existing state is returned rather than allocating another ticket.

If the same request ID is submitted with a different `userId`, the request is rejected rather than exposing another user's purchase state.

The initial `PROCESSING` record is claimed using:

```sql
INSERT ... ON CONFLICT DO NOTHING
```

This allows PostgreSQL's unique constraint to arbitrate concurrent submissions of the same request ID rather than relying on an unsafe application-level check-then-insert sequence.

The same `requestId` is retained throughout the complete purchase lifecycle and can also be used to poll the current purchase-request state.

---

## 4. Reservation-Based Payment Workflow

The initial implementation performed ticket allocation, simulated payment, and purchase completion inside one database transaction.

That design was simple but required holding a ticket row lock while payment was being processed.

This is undesirable for a realistic payment workflow because an external payment provider may take seconds to respond or may fail independently of the database transaction.

The current design therefore separates ticket allocation from payment using explicit reservations.

When `/buy` successfully acquires a ticket:

```text
Ticket:
AVAILABLE → RESERVED

PurchaseRequest:
PROCESSING → RESERVED

Reservation:
created as ACTIVE
```

The allocation transaction can then commit and release its database locks.

The buyer subsequently performs payment without requiring the original ticket-allocation transaction to remain open.

After successful payment, the reservation is confirmed:

```text
Ticket:
RESERVED → SOLD

PurchaseRequest:
RESERVED → PURCHASED

Reservation:
ACTIVE → CONFIRMED
```

This design avoids holding the original ticket-allocation lock across payment processing.

It also models an important production concept explicitly: temporary ownership of inventory while payment is in progress.

---

## 5. Reservation Expiry

A reservation cannot hold a ticket indefinitely.

Each reservation therefore has an immutable `expiresAt` deadline and starts in:

```text
ACTIVE
```

If the buyer does not successfully confirm the reservation before the deadline:

```text
Reservation:
ACTIVE → EXPIRED

PurchaseRequest:
RESERVED → RESERVATION_EXPIRED
```

The ticket can then either be made `AVAILABLE` again or transferred directly to a waiting buyer.

Reservation expiry is processed asynchronously using JobRunr.

A corresponding persisted `ScheduledTask` is created when the reservation is created.

If the reservation is successfully confirmed before its deadline, the logical expiry task is marked `CANCELLED`.

The expiry handler is also idempotent. If an expiry job executes after its reservation has already been confirmed, cancelled, or expired, it does not modify the terminal reservation state.

### Retaining `expiresAt`

`expiresAt` is not cleared when a reservation becomes `CONFIRMED`, `EXPIRED`, or `CANCELLED`.

Once the reservation is no longer `ACTIVE`, the timestamp no longer controls behavior, but it remains useful historical information describing the deadline that originally applied to that reservation.

---

## 6. Buyer Queue

When `/buy` cannot immediately acquire an available ticket, the request is not necessarily sold out.

If the sale still contains an `AVAILABLE` or `RESERVED` ticket, the purchase request enters the buyer queue:

```text
PurchaseRequest:
PROCESSING → QUEUED

BuyerQueueEntry:
ACTIVE
```

This includes the case where an `AVAILABLE` ticket exists but is temporarily inaccessible because another transaction currently holds its row lock.

A request is considered `SOLD_OUT` only when there are no tickets remaining in either:

```text
AVAILABLE
RESERVED
```

state.

This distinction is important because a `RESERVED` ticket may later return to circulation if its reservation expires.

---

## 7. FIFO Queue Ordering

Queue entries do not store a mutable numerical queue position.

Instead, eligible entries are selected using:

```text
created_at ASC, id ASC
```

`created_at` provides FIFO ordering.

The UUID `id` acts as a deterministic tie-breaker if multiple entries have the same timestamp.

Avoiding a stored queue-position number means the system does not need to renumber queue entries whenever buyers are fulfilled, expire, or cancel.

Strict global arrival fairness is not guaranteed under every concurrent locking schedule because `SKIP LOCKED` allows locked queue entries to be bypassed temporarily.

The ordering therefore represents FIFO among currently eligible and acquirable queue entries rather than a globally serialized ordering of every concurrent request.

---

## 8. Queue Promotion

When an active reservation expires, the service searches for the oldest eligible queue entry belonging to the same `SaleVersion`.

The queue-selection query uses row locking and:

```sql
FOR UPDATE ... SKIP LOCKED
```

The selected queue entry must:

- be `ACTIVE`,
- belong to the same sale,
- not have passed its queue expiry deadline.

If an eligible queued buyer exists:

```text
Old Reservation:
ACTIVE → EXPIRED

Old PurchaseRequest:
RESERVED → RESERVATION_EXPIRED

BuyerQueueEntry:
ACTIVE → FULFILLED

Queued PurchaseRequest:
QUEUED → RESERVED

New Reservation:
created as ACTIVE
```

The ticket remains `RESERVED` throughout the handoff rather than temporarily becoming globally available.

This prevents another `/buy` request from acquiring the ticket between expiration of the old reservation and assignment to the queued buyer.

The new reservation receives its own independent expiration deadline.

If no eligible queued buyer exists, the expired reservation releases the ticket:

```text
RESERVED → AVAILABLE
```

---

## 9. Queue Expiry

A buyer should not remain queued indefinitely.

Each queue entry therefore receives an `expiresAt` timestamp and a corresponding `QUEUE_ENTRY_EXPIRY` scheduled task.

If the queue deadline is reached before the buyer receives a ticket:

```text
BuyerQueueEntry:
ACTIVE → EXPIRED

PurchaseRequest:
QUEUED → QUEUE_EXPIRED
```

The purchase request is then terminal.

Queue expiry and queue promotion both lock the queue-entry row before changing its state.

This ensures that concurrent promotion and expiry cannot both successfully complete the same queue entry.

---

## 10. Scheduled Task Model

JobRunr is used to execute reservation-expiry and queue-expiry work asynchronously.

The application also persists its own `ScheduledTask` entity rather than treating the background scheduler as the only record of scheduled work.

Supported task types include:

```text
RESERVATION_EXPIRY
QUEUE_ENTRY_EXPIRY
```

Task states include:

```text
PENDING
PROCESSING
COMPLETED
FAILED
CANCELLED
```

Persisting the logical task separately provides an application-level representation of whether expiry work is still valid.

For example, when a reservation is confirmed, its reservation-expiry task becomes `CANCELLED`.

Similarly, when a queued buyer is promoted, that buyer's queue-expiry task is cancelled.

Handlers still verify current domain state when invoked. Correctness therefore does not depend solely on physically preventing JobRunr from invoking a previously scheduled job.

---

## 11. Reservation and Expiry Concurrency

Reservation confirmation and reservation expiry may occur concurrently near the reservation deadline.

The reservation row is therefore loaded using a pessimistic write lock before either operation performs its state transition.

This serializes competing changes to the same reservation.

The operation that obtains the lock observes the current reservation state and validates whether its transition is still legal.

The database therefore becomes the coordination point for the race rather than relying on in-memory synchronization.

A similar strategy is used for queue promotion versus queue expiry.

---

## 12. One Active Reservation per Ticket

Historical reservation rows are retained rather than deleted.

A ticket may therefore have multiple reservation records over its lifetime:

```text
Reservation A → EXPIRED
Reservation B → EXPIRED
Reservation C → CONFIRMED
```

However, only one reservation may be active for the ticket at a time.

PostgreSQL enforces this using a partial unique index:

```sql
CREATE UNIQUE INDEX uk_reservation_one_active_per_ticket
ON reservation (ticket_id)
WHERE status = 'ACTIVE';
```

When handing an expired ticket directly to a queued buyer, the old reservation's transition from `ACTIVE` to `EXPIRED` must be flushed before inserting the new `ACTIVE` reservation.

This ensures that the database observes the old reservation leaving the partial unique index before the replacement reservation is inserted.

---

## 13. Sale Reset

Each `/reset` creates a new `SaleVersion` and a complete set of `AVAILABLE` ticket rows rather than deleting or reusing the previous sale.

Previous sale versions are retained so that purchase, reservation, and queue history remains associated with the sale against which it occurred.

Concurrent resets are serialized using a PostgreSQL transaction-scoped advisory lock.

The reset transaction:

1. Acquires the reset advisory lock.
2. Creates the new inactive sale.
3. Creates all tickets associated with the new sale.
4. Deactivates the previous active sale.
5. Activates the new sale.
6. Commits.

The advisory lock is automatically released when the transaction commits or rolls back.

The new sale is not exposed as active until its complete inventory has been created. This prevents buyers from observing a partially initialized sale.

A purchase request that has already bound itself to a sale remains associated with that `SaleVersion` even if another reset later creates a new active sale.

---

## 14. Consistent Status

`/status` derives ticket state from persisted ticket records rather than maintaining a separate mutable inventory counter.

In particular, the number of sold tickets is derived from the persisted `SOLD` tickets.

This avoids synchronization problems between a separate counter and the ticket rows that actually represent inventory ownership.

The status operation uses a read-only `REPEATABLE_READ` transaction so that the active sale and the ticket snapshot observed during the request remain consistent.

---

## 15. Database Constraints

Important correctness rules are enforced in both the domain model and PostgreSQL where practical.

The schema contains constraints and indexes protecting invariants including:

- one active sale version at a time,
- unique `(sale_version_id, ticket_number)`,
- globally unique purchase `requestId`,
- valid ticket-state combinations,
- valid purchase-request state combinations,
- one queue entry per purchase request,
- valid buyer-queue state combinations,
- one active reservation per ticket,
- one reservation per purchase request,
- reservation and queue timestamp consistency.

The intention is that correctness should not depend solely on every application code path behaving correctly.

Invalid persisted states should also be rejected by PostgreSQL.

---

## 16. Performance Trade-offs

The initial schema did not contain an index specifically for `AVAILABLE` tickets.

Performance investigation showed that PostgreSQL was locating tickets for a sale and filtering sold rows when checking for remaining inventory.

A partial index was therefore added:

```sql
CREATE INDEX idx_ticket_available_by_sale
ON ticket (sale_version_id, ticket_number)
WHERE status = 'AVAILABLE';
```

`EXPLAIN ANALYZE` showed that this reduced database work for the available-ticket access pattern and allowed relevant existence checks to use the index efficiently.

However, controlled workload tests on the earlier immediate-purchase implementation did not show a consistent end-to-end throughput improvement.

With an inventory of only 100 tickets, this query was not the dominant system bottleneck.

The index was retained because it directly matches the access pattern and reduces unnecessary database work, but query-plan improvement is not treated as evidence of application-level throughput improvement.

Historical measurements are documented in `results/README.md`.

Those measurements predate the reservation and buyer-queue architecture and should not be interpreted as performance measurements of the current version until the updated architecture is benchmarked separately.

---

## 17. Why PostgreSQL Instead of Redis or Kafka

The current architecture intentionally uses PostgreSQL as the primary coordination mechanism.

Redis-based distributed locks, Kafka-based allocation, or an additional queueing system could be introduced, but they would add infrastructure and additional consistency boundaries.

For the current problem, PostgreSQL already provides:

- atomic transactions,
- row-level locking,
- `SKIP LOCKED`,
- uniqueness constraints,
- partial unique indexes,
- durable state,
- transaction isolation.

The buyer queue is also persisted in PostgreSQL, allowing queue ownership and ticket ownership to participate in the same transactional model.

Keeping these operations inside one datastore makes the important correctness properties easier to reason about and test.

Additional distributed infrastructure should be introduced only when scale or system requirements demonstrate a need for it.

---

## 18. Known Fairness Trade-off

The system prioritizes correctness and concurrent progress over strict global fairness.

Because ticket allocation and queue selection use `SKIP LOCKED`, a row currently locked by another transaction may be bypassed.

For example:

1. Buyer A temporarily locks an available ticket.
2. Buyer B cannot acquire that ticket and enters the queue.
3. Buyer A's transaction rolls back.
4. Buyer C may subsequently acquire the now-available ticket before Buyer B is promoted.

The system therefore does not guarantee that buyers receive tickets in exact request-arrival order.

The queue itself provides FIFO ordering for eligible queue entries, but it is not intended to provide a globally serialized fairness guarantee across every concurrent `/buy` transaction.

This behavior is an explicit trade-off rather than an accidental property of the implementation.

---

## 19. Deferred Optimization: Queue Becomes Impossible to Fulfill

If the final reserved ticket is successfully purchased while other buyers remain queued, those queue entries can no longer receive a ticket.

The current implementation leaves those requests in:

```text
QUEUED
```

until their individual queue deadlines expire:

```text
QUEUED → QUEUE_EXPIRED
```

A possible future optimization would detect when the final potentially available ticket becomes permanently `SOLD` and transition remaining queued purchase requests directly to `SOLD_OUT`.

This optimization is intentionally deferred because it is not required for ticket-allocation correctness and introduces additional coordination around bulk queue-state transitions.

---

## 20. Scope

The implementation focuses on making the core ticket lifecycle explicit and concurrency-safe while keeping the system small enough to reason about and test.

The current design includes:

- concurrent ticket allocation,
- idempotent purchase requests,
- temporary reservations,
- reservation expiry,
- simulated payment,
- purchase confirmation,
- persistent buyer queue,
- FIFO queue promotion,
- queue expiry,
- background scheduled work,
- sale versioning,
- database-enforced invariants.

Important next validation work includes:

- reservation confirmation racing reservation expiry,
- queue promotion racing queue expiry,
- multiple simultaneous reservation expirations,
- many concurrent buyers competing for limited inventory,
- large-scale load testing of the reservation-and-queue architecture.

Possible future production concerns include:

- external payment-provider orchestration,
- distributed load generation,
- scheduler recovery and operational monitoring,
- multi-instance deployment,
- multi-region database behavior,
- queue notification mechanisms instead of client polling.

The design deliberately avoids introducing additional distributed infrastructure until the requirements justify the additional complexity.