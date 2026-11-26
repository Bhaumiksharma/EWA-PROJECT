-- Smart Parking Allocation System : database setup
-- Safe to run more than once (IF NOT EXISTS / INSERT IGNORE).
-- To start from scratch: DROP DATABASE smart_parking; and run this file again.

CREATE DATABASE IF NOT EXISTS smart_parking;
USE smart_parking;

CREATE TABLE IF NOT EXISTS parking_floor (
    floor_id     INT PRIMARY KEY,
    floor_number INT NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS parking_slot (
    slot_id       VARCHAR(10) PRIMARY KEY,
    floor_id      INT NOT NULL,
    slot_number   INT NOT NULL,
    slot_type     ENUM('BIKE','CAR','EV') NOT NULL,
    distance_rank INT NOT NULL,
    is_occupied   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_floor_slot (floor_id, slot_number),
    CONSTRAINT fk_slot_floor FOREIGN KEY (floor_id) REFERENCES parking_floor (floor_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vehicle (
    vehicle_no   VARCHAR(15) PRIMARY KEY,
    vehicle_type ENUM('BIKE','CAR','EV') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS parking_ticket (
    ticket_id  VARCHAR(20) PRIMARY KEY,
    vehicle_no VARCHAR(15) NOT NULL,
    slot_id    VARCHAR(10) NOT NULL,
    entry_time DATETIME NOT NULL,
    exit_time  DATETIME NULL,
    amount     DECIMAL(10,2) NULL,
    status     ENUM('ACTIVE','CLOSED') NOT NULL,
    CONSTRAINT fk_ticket_vehicle FOREIGN KEY (vehicle_no) REFERENCES vehicle (vehicle_no),
    CONSTRAINT fk_ticket_slot    FOREIGN KEY (slot_id)    REFERENCES parking_slot (slot_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payment (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id  VARCHAR(20) NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    paid_at    DATETIME NOT NULL,
    CONSTRAINT fk_payment_ticket FOREIGN KEY (ticket_id) REFERENCES parking_ticket (ticket_id)
) ENGINE=InnoDB;

-- Floors and slots (2 floors, 9 slots each).
-- Distance rank 1 is closest to the entry gate on that floor.
INSERT IGNORE INTO parking_floor (floor_id, floor_number) VALUES (1, 1), (2, 2);

INSERT IGNORE INTO parking_slot (slot_id, floor_id, slot_number, slot_type, distance_rank, is_occupied) VALUES
('F1-B1', 1, 1, 'BIKE', 1, FALSE),
('F1-B2', 1, 2, 'BIKE', 2, FALSE),
('F1-B3', 1, 3, 'BIKE', 3, FALSE),
('F1-C1', 1, 4, 'CAR',  4, FALSE),
('F1-C2', 1, 5, 'CAR',  5, FALSE),
('F1-C3', 1, 6, 'CAR',  6, FALSE),
('F1-C4', 1, 7, 'CAR',  7, FALSE),
('F1-E1', 1, 8, 'EV',   8, FALSE),
('F1-E2', 1, 9, 'EV',   9, FALSE),
('F2-B1', 2, 1, 'BIKE', 1, FALSE),
('F2-B2', 2, 2, 'BIKE', 2, FALSE),
('F2-B3', 2, 3, 'BIKE', 3, FALSE),
('F2-C1', 2, 4, 'CAR',  4, FALSE),
('F2-C2', 2, 5, 'CAR',  5, FALSE),
('F2-C3', 2, 6, 'CAR',  6, FALSE),
('F2-C4', 2, 7, 'CAR',  7, FALSE),
('F2-E1', 2, 8, 'EV',   8, FALSE),
('F2-E2', 2, 9, 'EV',   9, FALSE);

-- Sample history so the reports show data on a fresh install.
-- Fees follow the regular rule: CAR Rs 40/hr, BIKE Rs 20/hr, rounded up to whole hours.
-- CAR 09:00-11:10 = 3 hours = 120.00 ; BIKE 18:30-19:05 = 1 hour = 20.00
INSERT IGNORE INTO vehicle (vehicle_no, vehicle_type) VALUES
('DL01AB1234', 'CAR'),
('DL02XY9876', 'BIKE');

INSERT IGNORE INTO parking_ticket (ticket_id, vehicle_no, slot_id, entry_time, exit_time, amount, status) VALUES
('TKT-SEED0001', 'DL01AB1234', 'F1-C1', '2026-11-20 09:00:00', '2026-11-20 11:10:00', 120.00, 'CLOSED'),
('TKT-SEED0002', 'DL02XY9876', 'F1-B1', '2026-11-21 18:30:00', '2026-11-21 19:05:00',  20.00, 'CLOSED');

INSERT IGNORE INTO payment (payment_id, ticket_id, amount, paid_at) VALUES
(1, 'TKT-SEED0001', 120.00, '2026-11-20 11:10:00'),
(2, 'TKT-SEED0002',  20.00, '2026-11-21 19:05:00');
