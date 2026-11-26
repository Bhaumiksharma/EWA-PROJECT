# Smart Parking Allocation System

Console-based multi-level parking application (CSL273 Minor Project, Problem Statement 9).
Several entry gates allocate compatible slots to arriving vehicles, and the system records vehicles,
slots, tickets and payments in MySQL and produces occupancy, free-slot, utilization and revenue reports.

Stack: Java 17, Maven, Spring Core (annotation config + DI, no Spring Boot), JDBC with MySQL 8, multithreading
with a fixed gate-thread pool.

## Project layout

    SmartParkingSystem/
    ├── pom.xml
    ├── README.md
    ├── database/
    │   └── smart_parking.sql           schema + sample data
    └── src/main/
        ├── resources/application.properties
        └── java/com/smartparking/
            ├── Main.java               Spring context + console menu
            ├── config/AppConfig.java   @Configuration, component scan, DataSource and billing beans
            ├── model/                  VehicleType, Vehicle, ParkingSlot, ParkingTicket, ParkingFloor
            ├── service/                ParkingService, SlotAllocationService, BillingService,
            │                           RegularBillingService, WeekendBillingService, ReportService,
            │                           NearestSlotComparator, ParkingException
            ├── repository/             TicketRepository (interface), ParkingRepository (JDBC),
            │                           TransactionRunner, PersistenceException
            └── threading/              EntryGateTask, GateThreadFactory

## Prerequisites

- JDK 17 or later (`java -version`)
- Maven 3.8+ (`mvn -version`)
- MySQL Server 8.x running on localhost:3306 (MySQL Workbench is optional)
- VS Code with the Extension Pack for Java (optional, for editing and running)

## Setup and run

1. Create the database. In MySQL Workbench, or from the command line:

       mysql -u root -p < database/smart_parking.sql

   This creates the `smart_parking` database, the five tables, 18 slots on 2 floors, and a few closed sample tickets.

2. Set your MySQL password in `src/main/resources/application.properties`:

       db.password=your_local_password

   Do not commit real credentials to a public repository.

3. Compile from the project root (the folder that contains `pom.xml`):

       mvn clean compile

4. Run the application:

       mvn exec:java

   Or open the folder in VS Code, let Maven resolve dependencies, and run `com.smartparking.Main`.

## Using the console menu

| Option | What it does |
|---|---|
| 1 | Park a vehicle: enter the number (e.g. `DL01AB1234`) and type (`BIKE`, `CAR` or `EV`) |
| 2 | Exit: closes the active ticket, computes the fee, records the payment, frees the slot |
| 3 | Slot map grouped by floor |
| 4 | Reports: occupancy, free slots, utilization by type, revenue |
| 5 | Concurrent simulation: 12 random vehicles through 3 gate threads, then a double-allocation check |
| 6 | Fault injection: the next entry fails after the slot is reserved, to show the rollback |

Restart the app at any time: active tickets and slot status are reloaded from MySQL.

## Live change: switching the billing policy

`ParkingService` receives its `BillingService` through `@Qualifier("billingService")`. The bean named
`billingService` is chosen in `AppConfig` from the `billing.policy` property:

1. Stop the app.
2. Set `billing.policy=weekend` in `application.properties`.
3. Start the app, park a vehicle on a Saturday or Sunday (or check the fee after exit), and compare the amount with the `regular` policy. The weekend fee is 1.5 times the regular fee.

## Design decisions

### Nearest-slot rule and compatibility

- Slot types are BIKE, CAR and EV. Each slot has one type. Each slot also has a `distance_rank` (1 = closest to the gate on its floor).
- Compatibility: a BIKE uses BIKE slots, a CAR uses CAR slots, an EV uses EV slots and falls back to CAR slots when no EV slot is free (see `VehicleType.compatibleSlotTypes()`).
- Nearest: the lowest floor first, then the lowest `distance_rank`, then the lowest slot number (see `NearestSlotComparator`). The result is deterministic.
- Preferred type first: the allocator only uses a fallback type when no slot of the vehicle's own type is free.

### Collections

| Collection | Where | Why |
|---|---|---|
| `HashMap<String, ParkingSlot>` | `ParkingService.slotsById` | O(1) lookup of a slot by ID when rebuilding tickets from the database |
| `ArrayList<ParkingSlot>` | `ParkingService.slotList` | Keeps the slots sorted by `NearestSlotComparator` for the allocator and for listings |
| `HashSet<String>` | `ParkingService.activeVehicles` | Fast duplicate check: is this vehicle already parked? |
| `HashMap<String, ParkingTicket>` | `ParkingService.ticketsByVehicle` | Maps a vehicle to its active ticket, so exit finds the ticket directly |
| `EnumMap<VehicleType, Integer>` | `ParkingRepository.countByType` | Compact per-type counts, with every type always present |
| `ConcurrentLinkedQueue` | `Main` simulation | Thread-safe collection that the gate threads write admitted tickets into |
| `Comparator<ParkingSlot>` | `NearestSlotComparator` | Defines the nearest-slot ordering in one place |

### Multithreading and thread safety

- The shared resource is the slot state (`slotList`, `ParkingSlot.occupied`).
- Gate threads come from a fixed pool of 3 threads (`GateThreadFactory` names them Gate-1 to Gate-3). Each vehicle is an `EntryGateTask`.
- Entry and exit both run under one `ReentrantLock`. The critical section covers the free-slot search, the database transaction and the in-memory update, so two gates cannot pick the same slot.
- Second guard in SQL: `UPDATE parking_slot SET is_occupied = TRUE WHERE slot_id = ? AND is_occupied = FALSE`. The code checks that exactly one row changed. If another process changed the row, the transaction rolls back.
- The in-memory state is only changed after the commit succeeds, so a failed transaction leaves memory and database in agreement.

### JDBC and transactions

- All statements are `PreparedStatement`s. Resources are closed with try-with-resources.
- Entry transaction (one connection, one commit): active-ticket check, vehicle upsert, conditional slot reservation, ticket insert.
- Exit transaction: ticket close (only if still ACTIVE), payment insert, slot release.
- `TransactionRunner` sets auto-commit off, commits when the work finishes, and rolls back on any exception.
- Retrieval and reporting queries: slot status join, active-ticket load, count by type (total, occupied, sessions), total revenue.

### Spring Core

- `AppConfig`: `@Configuration`, `@ComponentScan`, `@PropertySource`, a `DataSource` bean, and the selected `billingService` bean.
- `ParkingService` depends on the `TicketRepository` interface (constructor injection). `ParkingRepository` is the only implementation.
- `BillingService` has two implementations: `RegularBillingService` and `WeekendBillingService`. The active one is injected through `@Qualifier("billingService")` and selected by property.

### Billing assumptions (not fixed by the brief)

- Hourly rates: BIKE Rs 20, CAR Rs 40, EV Rs 30.
- Any started hour is charged, with a minimum of one hour (35 minutes = 1 hour; 61 minutes = 2 hours).
- Weekend policy: 1.5 times the regular fee when the vehicle enters on a Saturday or Sunday.

### Reports

- Occupancy, free slots, and utilization are computed from the database.
- "Current %" is occupied slots of a type right now, divided by the number of slots of that type.
- "Sessions" is the number of tickets ever issued for that type. The two are labelled separately in the report.

## Test checklist

| Test | How to run it | Expected result |
|---|---|---|
| Normal entry | Option 1 with a BIKE, a CAR and an EV | Each gets a free compatible slot; `parking_ticket` row is ACTIVE |
| Compatibility failure | Fill all CAR and EV slots, then park a CAR | "No free compatible slot", no new ticket |
| Duplicate vehicle | Park the same number twice | "already parked", no second ticket |
| Concurrent entry | Option 5 | Admitted + rejected = 12; "Slots allocated twice: 0" |
| Last-slot race | Leave one CAR slot free, then run option 5 | At most one simulated CAR gets the slot |
| Exit | Option 2 with a parked vehicle | Ticket CLOSED, payment row added, slot FREE |
| Invalid exit | Option 2 with an unknown number, or twice for the same number | "No active ticket", no duplicate payment |
| Rollback | Option 6, then option 1 | Entry rejected and rolled back; the slot stays FREE, no ticket or vehicle row is left behind |
| Reports | Option 4 | Total = occupied + free; revenue equals the sum of `payment.amount` |
| Restart | Park a vehicle, stop the app, start it again, option 3 | The slot is still OCCUPIED |

Check the database after each step, for example:

    SELECT * FROM parking_ticket ORDER BY entry_time DESC LIMIT 5;
    SELECT slot_id, is_occupied FROM parking_slot WHERE is_occupied = TRUE;
    SELECT SUM(amount) FROM payment;

## Troubleshooting

- `Access denied for user 'root'`: the password in `application.properties` is wrong.
- `Public Key Retrieval is not allowed` or SSL errors: the URL already contains `useSSL=false&allowPublicKeyRetrieval=true`. Leave it as it is for localhost.
- `Communications link failure`: MySQL is not running, or it is not on port 3306.
- `Unknown database 'smart_parking'`: run `database/smart_parking.sql` first.
- To reset everything: `DROP DATABASE smart_parking;` and then run the SQL script again.

## Known limitations

- Single JVM only. The lock protects one running application. Multiple app instances would rely on the conditional SQL update alone for slot safety.
- Vehicle numbers are not checked against any state format. The check is only that they are 4-15 letters, digits or hyphens.
- `ParkingService` holds the lock during the database transaction. That keeps the logic simple but serializes entries and exits. This is acceptable for a console demo.
