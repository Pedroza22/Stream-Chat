-- Tabla de idempotencia para consumidores de RabbitMQ y Kafka.
-- Antes de procesar un evento, el consumidor intenta insertar su eventId aquí.
-- Si ya existe (UNIQUE), lo descarta sin producir efectos de negocio duplicados.
CREATE TABLE processed_events (
    event_id     UUID        NOT NULL,
    consumer     VARCHAR(100) NOT NULL,   -- nombre del consumidor (ej: "message-created")
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer)
);

-- Índice para limpiezas periódicas por fecha (opcional, para mantenimiento futuro)
CREATE INDEX idx_processed_events_at ON processed_events (processed_at);
