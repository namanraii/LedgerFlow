import json
import logging
import os
import pika

logger = logging.getLogger("fraud-service.messaging")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
EXCHANGE_NAME = "ledgerflow.alerts"
QUEUE_NAME = "fraud-alerts"

def publish_fraud_alert(alert_payload: dict) -> bool:
    """
    Publishes fraud alert to RabbitMQ.
    If RabbitMQ is down, logs failure and returns False without failing the caller.
    """
    try:
        credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
        parameters = pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            credentials=credentials,
            connection_attempts=1,
            socket_timeout=0.8
        )
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()
        channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="fanout", durable=True)
        channel.queue_declare(queue=QUEUE_NAME, durable=True)
        channel.queue_bind(exchange=EXCHANGE_NAME, queue=QUEUE_NAME)

        channel.basic_publish(
            exchange=EXCHANGE_NAME,
            routing_key="",
            body=json.dumps(alert_payload),
            properties=pika.BasicProperties(
                delivery_mode=2, # persistent message
                content_type="application/json"
            )
        )
        connection.close()
        logger.info(f"Published alert to RabbitMQ for account {alert_payload.get('account_id')}")
        return True
    except Exception as e:
        logger.warn(f"RabbitMQ publish unavailable ({type(e).__name__}): {e}. Durable flag preserved in Ledger.")
        return False
