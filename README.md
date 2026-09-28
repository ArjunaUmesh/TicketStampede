# Ticket Stampede

Ticket Stampede is a concurrent ticket-selling service built with Spring Boot and PostgreSQL.

It models a high-contention ticket sale where buyers compete for limited inventory, receive temporary reservations, complete simulated payments, and wait in a buyer queue when tickets are temporarily unavailable.

The system is designed around correctness under concurrency. It uses PostgreSQL transactions, row-level locking, `FOR UPDATE SKIP LOCKED`, idempotent purchase requests, database constraints, and scheduled expiry jobs to coordinate ticket allocation without application-level distributed locks.

## Features

- Concurrent ticket allocation
- Temporary ticket reservations
- Reservation expiry and automatic ticket release
- Simulated payment processing
- Purchase confirmation
- Idempotent purchase requests
- FIFO buyer queue
- Automatic promotion of queued buyers
- Queue-entry expiry
- Sale reset/versioning
- Purchase-request polling

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- JobRunr
- Maven

## Core Purchase Flow

A purchase is not completed immediately when a buyer calls `/buy`.

Instead, the service uses a reservation-based lifecycle.

```text
AVAILABLE ticket
      |
      v
POST /buy
      |
      v
RESERVED
      |
      v
Payment
      |
      v
Confirm Reservation
      |
      v
PURCHASED
```

If the buyer does not confirm the reservation before its deadline:

```text
RESERVED
    |
    v
Reservation expires
    |
    v
RESERVATION_EXPIRED
```

The ticket can then be released or transferred directly to a waiting buyer.

## Buyer Queue

When `/buy` cannot immediately acquire an available ticket, but tickets for the sale are still either `AVAILABLE` or `RESERVED`, the request enters the buyer queue.

```text
POST /buy
    |
    v
QUEUED
```

If a reservation later expires, the oldest eligible queued buyer is promoted:

```text
Buyer A
RESERVED
    |
    | reservation expires
    v
RESERVATION_EXPIRED

Buyer B
QUEUED
    |
    v
RESERVED
```

A new reservation is created for Buyer B and receives its own expiration deadline.

If no ticket becomes available before the buyer's queue deadline:

```text
QUEUED
   |
   v
QUEUE_EXPIRED
```

If all tickets are already permanently `SOLD`, new purchase requests return:

```text
SOLD_OUT
```

## Purchase Request Lifecycle

A `PurchaseRequest` represents a logical attempt to acquire a ticket.

Possible states include:

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

Typical successful flow:

```text
PROCESSING
    |
    v
RESERVED
    |
    v
PURCHASED
```

Queued successful flow:

```text
PROCESSING
    |
    v
QUEUED
    |
    v
RESERVED
    |
    v
PURCHASED
```

Queue expiry:

```text
PROCESSING
    |
    v
QUEUED
    |
    v
QUEUE_EXPIRED
```

The same client-provided `requestId` identifies the purchase request throughout its lifecycle.

## Ticket Lifecycle

Tickets use three states:

```text
AVAILABLE
RESERVED
SOLD
```

`RESERVED` represents temporary ownership.

The permanent ticket holder is recorded only after the reservation is successfully confirmed and the ticket transitions to `SOLD`.

## Reservation Lifecycle

Reservations use:

```text
ACTIVE
CONFIRMED
EXPIRED
CANCELLED
```

Each active reservation has an `expiresAt` timestamp.

The timestamp is retained after completion as historical information even though it no longer controls the reservation once the reservation has left the `ACTIVE` state.

## Queue Lifecycle

Buyer queue entries use:

```text
ACTIVE
FULFILLED
EXPIRED
CANCELLED
```

Queue entries are ordered using:

```text
created_at ASC, id ASC
```

This provides deterministic FIFO ordering when selecting the next eligible buyer.

## Concurrency Model

### Ticket Allocation

Ticket allocation uses PostgreSQL:

```sql
FOR UPDATE SKIP LOCKED
```

An available ticket row is locked while the purchase request creates its reservation.

This allows concurrent transactions to claim different tickets without serializing all buyers through an application-level lock.

### Lock Contention vs Sold Out

An empty `SKIP LOCKED` result does not necessarily mean that the sale is sold out.

An `AVAILABLE` ticket may currently be locked by another transaction.

The service therefore distinguishes:

```text
No immediately acquirable ticket
        |
        +-- AVAILABLE or RESERVED ticket exists --> QUEUED
        |
        +-- no AVAILABLE or RESERVED ticket ------> SOLD_OUT
```

The implementation intentionally does not perform bounded retry/jitter around ticket acquisition.

This means that under contention, a buyer may enter the queue while another transaction temporarily holds an available ticket. This trades strict arrival fairness for a simpler database-coordinated allocation model.

### Queue Promotion

When a reservation expires, the service searches for the oldest eligible queue entry for the same sale version.

Queue selection also uses row locking with `SKIP LOCKED`.

This allows concurrent reservation expirations to promote different queued buyers without assigning the same queue entry multiple times.

### Database Constraints

Correctness is not enforced solely by application logic.

The database also contains constraints and indexes protecting important invariants, including uniqueness of active reservations for a ticket and valid state combinations.

## Scheduled Expiry

Reservation and queue expiration are handled asynchronously using JobRunr.

The service stores its own `ScheduledTask` records with states:

```text
PENDING
PROCESSING
COMPLETED
FAILED
CANCELLED
```

Supported scheduled task types include:

```text
RESERVATION_EXPIRY
QUEUE_ENTRY_EXPIRY
```

When a reservation is confirmed, its logical expiry task is cancelled.

Expiry handlers are also designed to be idempotent: if an already-completed or cancelled task is invoked again, it does not modify the completed purchase.

## Idempotency

Clients provide a UUID `requestId` when calling `/buy`.

The same `requestId` represents the same logical purchase attempt.

Repeating `/buy` with the same `requestId` returns the existing purchase state instead of allocating another ticket.

A request ID is also associated with its original user so that another user cannot reuse it to access the same purchase request.

## Requirements

- Java 21
- Maven
- PostgreSQL

## Database Setup

Create the PostgreSQL database used by the application:

```sql
CREATE DATABASE ticket_stampede_reservation;
```

Configure the application database user and set the password through the environment:

```bash
export TICKET_STAMPEDE_DB_PASSWORD=<your-password>
```

The remaining datasource configuration is defined in `application.properties`.

Flyway migrations run automatically when the application starts and create or update the required tables, constraints, and indexes.

Hibernate schema generation is disabled; Hibernate validates the Flyway-managed schema instead.

## Running the Application

From the project root:

```bash
./mvnw spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

The JobRunr dashboard is available at:

```text
http://localhost:8000/dashboard
```

## API

### Reset a Sale

Creates a new active sale with the requested ticket capacity.

```http
POST /reset
Content-Type: application/json
```

Example:

```json
{
  "capacity": 100
}
```

A new sale version and its tickets are created.

### Request a Ticket

```http
POST /buy
Content-Type: application/json
```

Example:

```json
{
  "userId": "user-1",
  "requestId": "3fa85f64-5717-4562-b3fc-2c963f660001"
}
```

Depending on current inventory, the request may return:

```text
RESERVED
QUEUED
SOLD_OUT
```

A `RESERVED` response contains the allocated ticket and reservation information.

A `QUEUED` response contains no ticket or reservation yet.

### Create a Payment

The payment endpoint simulates payment processing for an active reservation.

A successful payment produces a payment ID that can subsequently be used to confirm the reservation.

### Confirm a Reservation

The confirmation endpoint verifies the simulated payment and confirms the associated reservation.

Successful confirmation performs the state transition:

```text
Ticket:          RESERVED → SOLD
PurchaseRequest: RESERVED → PURCHASED
Reservation:     ACTIVE   → CONFIRMED
```

The associated reservation-expiry task is cancelled.

### Poll a Purchase Request

A client can poll the purchase request using the same `requestId` originally supplied to `/buy`.

```http
GET /purchase-requests/{requestId}
```

For example, a queued client may observe:

```text
QUEUED
```

and later:

```text
RESERVED
```

without creating a new purchase request.

This allows the client to discover when a queued request has received a reservation.

### Sale Status

```http
GET /status
```

Returns information about the current sale and ticket state.

## Example Queue Handoff

Assume the sale contains one ticket.

Buyer A calls `/buy`:

```text
Buyer A        → RESERVED
Ticket #1      → RESERVED
Reservation A  → ACTIVE
```

Buyer B calls `/buy` while Buyer A holds the ticket:

```text
Buyer B → QUEUED
```

Buyer A does not complete payment before the reservation deadline.

The reservation expiry job executes:

```text
Buyer A PurchaseRequest → RESERVATION_EXPIRED
Reservation A           → EXPIRED
```

Instead of making the ticket publicly available first, the service can transfer it directly to the next eligible queued buyer:

```text
Buyer B PurchaseRequest → RESERVED
Reservation B           → ACTIVE
Ticket #1               → remains RESERVED
```

Buyer B can then complete payment and confirmation:

```text
Buyer B PurchaseRequest → PURCHASED
Reservation B           → CONFIRMED
Ticket #1               → SOLD
```

## Correctness Invariants

The implementation is designed to preserve several important invariants:

1. A ticket cannot be permanently sold to multiple users.
2. The number of sold tickets cannot exceed the configured sale capacity.
3. A purchase request cannot acquire multiple tickets through retries.
4. A ticket can have at most one active reservation.
5. A confirmed reservation corresponds to a sold ticket.
6. A queued purchase request does not own a ticket until it is promoted.
7. A queue entry can only be fulfilled once.
8. Expiration and confirmation must not both successfully take ownership of the same active reservation.
9. Queue promotion and queue expiry must not both successfully complete the same queue entry.
10. Re-execution of completed/cancelled scheduled expiry work must not corrupt terminal state.

These guarantees are implemented using a combination of transactions, pessimistic row locking, database constraints, idempotent request handling, and explicit domain-state transitions.

## Verified Functional Scenarios

The reservation and buyer-queue implementation has been manually verified for the following flows:

```text
AVAILABLE → RESERVED
```

```text
RESERVED → RESERVATION_EXPIRED
```

```text
QUEUED → RESERVED
```

```text
QUEUED → RESERVED → PURCHASED
```

```text
QUEUED → QUEUE_EXPIRED
```

```text
RESERVED → PURCHASED
```

and:

```text
all tickets SOLD → new request returns SOLD_OUT
```

Polling a purchase request has also been verified across queue promotion and purchase completion.

## Concurrency Testing

The project originally included load testing for concurrent ticket allocation, including workloads with tens of thousands of purchase requests.

The reservation and buyer-queue architecture introduces additional concurrency scenarios that require separate validation.

Important scenarios include:

- multiple buyers competing for limited available tickets,
- multiple buyers entering the queue concurrently,
- multiple reservation expirations promoting queued buyers concurrently,
- reservation confirmation racing reservation expiry,
- queue expiry racing queue promotion,
- duplicate `/buy` requests using the same `requestId`,
- sale reset interacting with active purchase traffic.

Small deterministic concurrency tests should be used first to validate invariants before running larger stress workloads.

A larger workload can then simulate tens of thousands of concurrent buyers and verify the final database state against the expected invariants.

## Naive Implementation and Race-Condition Experiment

An earlier version of the project includes a deliberately unsafe ticket-allocation implementation on the `naiive` branch.

That implementation selects an available ticket without the row-locking strategy used by the final allocation design.

Under concurrent load, multiple transactions can observe the same ticket before the competing transaction completes, demonstrating why database-coordinated allocation is necessary.

The experiment is retained as a comparison against the concurrency-safe implementation.

Historical benchmark and naive-run results should be interpreted as measurements of the earlier immediate-purchase architecture rather than the current reservation-and-queue implementation.

## Design Philosophy

Ticket Stampede intentionally keeps coordination primarily inside PostgreSQL rather than introducing additional distributed infrastructure prematurely.

The current design does not require:

- Redis-based distributed locks
- Kafka-based ticket allocation
- application-wide synchronized locks

PostgreSQL already provides the transactional isolation, row locking, uniqueness enforcement, and atomic state transitions required for the current architecture.

Additional infrastructure would only be introduced if future scale or architectural requirements justified the added operational complexity.

## Project Documentation

- `DECISIONS.md` — architecture decisions, concurrency reasoning, alternatives, and trade-offs.
- `src/main/resources/db/migration/` — Flyway schema migrations.
- `results/` — historical load-test and performance investigation artifacts.
- `logs/` — retained AI-assisted development logs.

## Current Status

Implemented:

- ticket allocation
- idempotent purchase requests
- temporary reservations
- simulated payments
- purchase confirmation
- reservation expiry
- buyer queue
- FIFO queue promotion
- queue expiry
- purchase-request polling
- scheduled background expiry

Next:

- targeted concurrency tests for reservation and queue races
- larger concurrent buyer stress test
- validation of final database invariants under load
- updated performance measurements for the reservation-and-queue architecture