import json
import logging
import os
import threading
import time
import pika

logger = logging.getLogger("fraud-service.messaging")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
EXCHANGE_NAME = "ledgerflow.alerts"
QUEUE_NAME = "fraud-alerts"


class AlertPublisher:
    """Reuses one broker connection and backs off when the broker is down.

    Opening a fresh connection on every flagged payment added a timeout to the
    advisory path whenever RabbitMQ was absent. A short circuit keeps that
    failure from taxing the payment rail.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._connection = None
        self._channel = None
        self._down_until = 0.0

    def publish(self, alert_payload: dict) -> bool:
        if time.time() < self._down_until:
            return False
        with self._lock:
            try:
                channel = self._channel_locked()
                channel.basic_publish(
                    exchange=EXCHANGE_NAME,
                    routing_key="",
                    body=json.dumps(alert_payload),
                    properties=pika.BasicProperties(
                        delivery_mode=2,
                        content_type="application/json",
                    ),
                )
                logger.info("Published alert to RabbitMQ for account %s", alert_payload.get("account_id"))
                return True
            except Exception as exc:
                logger.warning("RabbitMQ publish unavailable (%s): %s", type(exc).__name__, exc)
                self._reset_locked()
                self._down_until = time.time() + 8.0
                return False

    def _channel_locked(self):
        if self._channel is not None and self._channel.is_open:
            return self._channel
        credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
        parameters = pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            credentials=credentials,
            connection_attempts=1,
            socket_timeout=0.4,
        )
        self._connection = pika.BlockingConnection(parameters)
        channel = self._connection.channel()
        channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="fanout", durable=True)
        channel.queue_declare(queue=QUEUE_NAME, durable=True)
        channel.queue_bind(exchange=EXCHANGE_NAME, queue=QUEUE_NAME)
        self._channel = channel
        return channel

    def _reset_locked(self):
        self._channel = None
        connection = self._connection
        self._connection = None
        if connection is not None:
            try:
                connection.close()
            except Exception:
                pass


_publisher = AlertPublisher()


def publish_fraud_alert(alert_payload: dict) -> bool:
    """Publishes a fraud alert. A down broker returns False and never fails the caller."""
    return _publisher.publish(alert_payload)
