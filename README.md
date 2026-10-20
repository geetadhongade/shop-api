# Shop API

A REST backend for a small online shop, built with Spring Boot and PostgreSQL.
Customers sign up, browse products, place orders and pay for them. Order totals
can be converted to other currencies using live exchange rates.

## Features

- **Products** – create, read, update, delete; product types: ELECTRONICS, CLOTHING, FOOD, COSMETICS, DRUGS
- **Customers** – sign-up with username and BCrypt-hashed password, partial updates, soft delete
- **Orders** – place an order with several items, list a customer's orders, cancel pending orders
- **Payments** – one payment per order (CARD, CASH or UPI); the amount must match the order total
- **Currency conversion** – order totals in any currency, using live rates from [Frankfurter](https://frankfurter.dev)
- Consistent JSON error responses
- Request ID (`X-Request-Id`) on every log line, so one request can be followed through the logs
- Limits on concurrent requests (Resilience4j bulkhead), per-endpoint database timeouts
- Retry and circuit breaker around the exchange-rate API
- Optimistic locking: two updates to the same record at the same time can't overwrite each other
- Plain-text rolling log files

## Tech stack

| Part | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Web, Spring Data JPA, Validation) |
| Database | PostgreSQL 16, schema managed by Flyway |
| Resilience | Resilience4j (bulkhead, retry, circuit breaker) |
| Tests | JUnit 5, Mockito, MockMvc, JaCoCo coverage |
| Packaging | Docker + Docker Compose |

## Quick start (Docker – recommended)

You only need [Docker Desktop](https://www.docker.com/products/docker-desktop/). Java and PostgreSQL run inside containers.

```bash
git clone https://github.com/geetadhongade/shop-api.git
cd shop-api
docker compose up --build -d
```

This starts two containers:

| Container | What | Port on your machine |
|---|---|---|
| `app` | The Shop API | `8080` |
| `db` | PostgreSQL 16 (database `shop`) | `5433` |

The app waits for the database to be ready, then creates all tables automatically.
Check it works:

```bash
curl http://localhost:8080/api/products
```

Useful commands:

```bash
docker compose logs -f app     # watch the app's logs
docker compose down            # stop (your data is kept)
docker compose down -v         # stop and delete all data
```

The database password defaults to `shop_local_dev` (for local development only).
To use your own, set `POSTGRES_PASSWORD` before starting:

```bash
POSTGRES_PASSWORD=my-secret docker compose up -d
```

## Running without Docker

Requirements: Java 21 and a PostgreSQL database called `shop`.

1. Create the database: `createdb shop`
2. Set your database user and password (the defaults in `application.properties` are for the author's machine):
   ```bash
   export SPRING_DATASOURCE_USERNAME=your_user
   export SPRING_DATASOURCE_PASSWORD=your_password
   ```
3. Start the app (the Maven wrapper downloads Maven for you):
   ```bash
   ./mvnw spring-boot:run
   ```

## API

Base URL: `http://localhost:8080`. List endpoints are paged: `?page=0&size=20&sort=name`.

### Products

| Method | Path | Description |
|---|---|---|
| GET | `/api/products` | List products |
| GET | `/api/products/{id}` | Get a product |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product (not allowed if it is in any order) |

### Customers

| Method | Path | Description |
|---|---|---|
| GET | `/api/customers` | List customers |
| GET | `/api/customers/{id}` | Get a customer |
| POST | `/api/customers` | Sign up a customer with username and password |
| PUT | `/api/customers/{id}` | Update a customer (only the fields you send are changed) |
| DELETE | `/api/customers/{id}` | Delete a customer (soft delete; their pending orders are cancelled) |

### Orders

| Method | Path | Description |
|---|---|---|
| POST | `/api/orders` | Place an order |
| GET | `/api/orders/{id}` | Get an order with its items |
| GET | `/api/orders?customerId={id}` | List a customer's orders (newest first) |
| GET | `/api/orders/{id}/products` | List the products in an order |
| GET | `/api/orders/{id}/total?currency=EUR` | Order total converted from USD |
| POST | `/api/orders/{id}/cancel` | Cancel an order (only while PENDING) |

### Payments

| Method | Path | Description |
|---|---|---|
| POST | `/api/payments` | Pay for an order |
| GET | `/api/payments/{id}` | Get a payment |
| GET | `/api/orders/{orderId}/payment` | Get the payment for an order |

### Example: from product to payment

```bash
# 1. Create a product
curl -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Headphones","type":"ELECTRONICS","price":120.50}'

# 2. Sign up a customer
curl -X POST http://localhost:8080/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","phone":"+1 555 0100",
       "username":"janedoe","password":"secret123","city":"Austin"}'

# 3. Place an order (2 x Headphones = 241.00)
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1,"items":[{"productId":1,"quantity":2}]}'

# 4. Pay for it (the amount must equal the order total)
curl -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1,"amount":241.00,"method":"CARD"}'

# 5. See the total in euros
curl "http://localhost:8080/api/orders/1/total?currency=EUR"
```

### Errors

Every error has the same JSON shape:

```json
{
  "timestamp": "2026-10-20T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "Email is already taken",
  "fieldErrors": null
}
```

| Status | When |
|---|---|
| 400 | Invalid input (`fieldErrors` lists each problem) |
| 404 | Record or endpoint not found |
| 409 | Business rule broken, duplicate data, or the record was changed by someone else |
| 503 | Server busy, request took too long, or the currency service is unavailable (see `Retry-After`) |

## Business rules

- Email and username must be unique. A deleted customer's email can't be reused.
- Prices are in USD. Each order item keeps the price from the moment the order was placed.
- A product can appear only once per order (use `quantity` for more).
- Only PENDING orders can be paid or cancelled.
- Each order can have only one payment, and paying marks the order as PAID.

## Logs

Logs go to the console and to `logs/shop-api.log` (rolled daily and at 10 MB, kept 30 days).
Every line carries the request ID:

```
INFO [reqId=3f9c...] RequestIdFilter : --> POST /api/orders
INFO [reqId=3f9c...] LoggingAspect   : OrderService.createOrder(..) completed in 14 ms
INFO [reqId=3f9c...] RequestIdFilter : <-- POST /api/orders 201 (18 ms)
```

Send your own `X-Request-Id` header to choose the ID; it is returned in the response header too.
To follow one request: `grep "reqId=3f9c" logs/shop-api.log`.

## Tests

```bash
./mvnw verify
```

Runs all unit tests (with mocked data) and writes a coverage report to
`target/site/jacoco/index.html`. One startup test (`ShopApiApplicationTests`) needs the
PostgreSQL database to be running.

## Project structure

```
src/main/java/com/example/shop
├── controller   REST endpoints
├── service      business logic and transactions
├── repository   database access (Spring Data JPA)
├── entity       database tables as Java classes
├── dto          request and response bodies
├── exception    custom errors and the global error handler
├── client       exchange-rate API client
├── config       beans (password encoder, HTTP client)
└── logging      request ID filter and timing aspect
src/main/resources
├── application.properties
└── db/migration   Flyway SQL scripts (V1–V7)
```
