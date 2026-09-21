# Ticket Stampede

A concurrent ticket-selling service built with Spring Boot and PostgreSQL.

The service models a high-contention sale in which 50,000 buyers compete for 100 tickets. It provides idempotent purchase requests, concurrent ticket allocation, atomic sale resets, and status reporting while preserving the ticket-sale correctness invariants under load.

## Requirements

- Java 21
- Maven
- PostgreSQL

## Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE ticket_stampede;
```

Configure the database password through the environment:

```bash
export TICKET_STAMPEDE_DB_PASSWORD=<your-password>
```

The application connects using the configuration in `application.properties`.

Flyway migrations run automatically when the application starts and create the required tables, constraints, and indexes.

## Running the Seller

From the project root:

```bash
./mvnw spring-boot:run
```

The seller starts on:

```text
http://localhost:8080
```

## API

### Reset a Sale

Creates a new sale with the requested ticket capacity.

```http
POST /reset
Content-Type: application/json
```

```json
{
  "capacity": 100
}
```

Example response:

```json
{
  "saleVersionId": "<uuid>",
  "capacity": 100,
  "startedAt": "<timestamp>"
}
```

### Buy a Ticket

```http
POST /buy
Content-Type: application/json
```

```json
{
  "userId": "user-1",
  "requestId": "<uuid>"
}
```

A successful purchase returns the allocated ticket number. Repeating the request with the same `requestId` returns the same terminal result.

When no tickets remain, the request returns a `SOLD_OUT` result.

### Sale Status

```http
GET /status
```

Returns the current sale capacity, number of sold tickets, and the complete list of ticket holders.

## Load Testing

The project includes an asynchronous buyer/load client that generates concurrent purchase requests and duplicate request IDs.

The primary performance workload uses:

- 100 tickets
- 50,000 buyers
- 66,666 HTTP requests including duplicate requests
- concurrency levels from 10 to 300

For each workload the client records:

- throughput,
- p50 latency,
- p99 latency,
- error count,
- correctness invariant result.

The detailed benchmark results and bottleneck investigation are available in [`results/README.md`](results/README.md).

## Correctness

The load client verifies that:

1. The service never sells more tickets than the configured capacity.
2. The same ticket number is never issued twice.
3. Reusing the same `requestId` never allocates a second ticket.
4. `/status` agrees with the tickets observed as successfully issued.

PostgreSQL transactions, row locking, uniqueness constraints, and idempotent purchase requests are used to preserve these guarantees under concurrent requests.

## Concurrency Approach

Ticket allocation uses PostgreSQL `FOR UPDATE SKIP LOCKED` so concurrent buyers can claim different available ticket rows without relying on application-level locks.

An empty `SKIP LOCKED` result is not immediately treated as sold out because remaining AVAILABLE tickets may currently be locked by another transaction. The service distinguishes contention from genuine sell-out and performs bounded retries for temporary contention.

Concurrent sale resets are serialized using a PostgreSQL transaction-scoped advisory lock.

More detailed reasoning and trade-offs are documented in [`DECISIONS.md`](DECISIONS.md).

## Performance Investigation

The service was tested across concurrency levels from 10 to 300.

The investigation included:

- throughput and latency scaling,
- HikariCP connection-pool saturation,
- payment-latency diagnostics,
- PostgreSQL `EXPLAIN ANALYZE`,
- a partial index for AVAILABLE tickets,
- controlled V1/V2 performance comparison,
- behavior under a simulated 10-second datastore slowdown.

All correctness invariants continued to pass throughout the primary performance sweeps and datastore degradation test.

See [`results/README.md`](results/README.md) for the measurements, graphs, query plans, and conclusions.

## Project Documentation

- [`DECISIONS.md`](DECISIONS.md) — architecture, concurrency decisions, and trade-offs.
- [`results/README.md`](results/README.md) — performance investigation and results.
- [`results/explain/v1.txt`](results/explain/v1.txt) — query plans before the partial index.
- [`results/explain/v2.txt`](results/explain/v2.txt) — query plans after the partial index.
- [`logs/`](logs/) — AI-assisted development logs.

## Concurrency Scenarios

The project includes targeted concurrency scenarios for exercising race conditions and validating behavior beyond the standard API flow.

The scenarios cover:

- many buyers purchasing concurrently from the same sale,
- purchases occurring concurrently with a sale reset,
- purchases occurring concurrently with `/status` reads,
- multiple concurrent `/reset` requests,
- purchases, resets, and status requests running concurrently.

These scenarios are intended to exercise transaction boundaries, ticket allocation, reset behavior, idempotency, and status consistency under concurrent operations.

They can be run using the `ConcurrencyScenario` client:

```text
/src/main/java/org/ticketstampede/client/ConcurrencyScenario.java
```

The individual scenarios can be enabled from its `main` method depending on the behavior being tested.