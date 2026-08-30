import json
import logging
import os
import threading
import time
from collections import deque
import pika

logger = logging.getLogger("notification-service.consumer")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
EXCHANGE_NAME = "ledgerflow.alerts"
QUEUE_NAME = "fraud-alerts"

class AlertBuffer:
    def __init__(self, maxlen: int = 100):
        self.buffer = deque(maxlen=maxlen)
        self.lock = threading.Lock()
        self.listeners = []

    def add(self, alert: dict):
        with self.lock:
            alert["received_at"] = time.time()
            self.buffer.appendleft(alert)
            for listener in list(self.listeners):
                try:
                    listener(alert)
                except Exception as e:
                    logger.debug(f"Error notifying listener: {e}")

    def get_all(self):
        with self.lock:
            return list(self.buffer)

    def subscribe(self, callback):
        self.listeners.append(callback)

    def unsubscribe(self, callback):
        if callback in self.listeners:
            self.listeners.remove(callback)

alert_store = AlertBuffer()

class RabbitMQConsumerThread(threading.Thread):
    def __init__(self, alert_buffer: AlertBuffer):
        super().__init__(daemon=True, name="rabbitmq-consumer-thread")
        self.alert_buffer = alert_buffer
        self.running = True

    def run(self):
        while self.running:
            try:
                credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
                parameters = pika.ConnectionParameters(
                    host=RABBITMQ_HOST,
                    port=RABBITMQ_PORT,
                    credentials=credentials,
                    connection_attempts=1,
                    socket_timeout=2.0
                )
                connection = pika.BlockingConnection(parameters)
                channel = connection.channel()
                channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="fanout", durable=True)
                channel.queue_declare(queue=QUEUE_NAME, durable=True)
                channel.queue_bind(exchange=EXCHANGE_NAME, queue=QUEUE_NAME)

                logger.info("Connected to RabbitMQ, consuming from fraud-alerts queue")

                for method_frame, properties, body in channel.consume(QUEUE_NAME, auto_ack=True, inactivity_timeout=1):
                    if not self.running:
                        break
                    if body:
                        try:
                            payload = json.loads(body.decode("utf-8"))
                            self.alert_buffer.add(payload)
                            logger.info(f"Consumed fraud alert for account {payload.get('account_id')}")
                        except Exception as e:
                            logger.error(f"Error parsing alert message: {e}")

                connection.close()
            except Exception as e:
                logger.debug(f"RabbitMQ connection pending/retryable ({type(e).__name__}): {e}")
                time.sleep(3)

    def stop(self):
        self.running = False
