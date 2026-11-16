CREATE TABLE demo_control (
 id integer PRIMARY KEY CHECK (id = 1),
 probe_failure boolean NOT NULL DEFAULT false
);
INSERT INTO demo_control(id, probe_failure) VALUES (1, false);
