import json
import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

_producer = None


async def start_kafka_producer() -> None:
    global _producer
    try:
        from aiokafka import AIOKafkaProducer

        bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        _producer = AIOKafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            acks="all",
            enable_idempotence=True,
        )
        await _producer.start()
        logger.info("Kafka anomaly producer started — bootstrap=%s", bootstrap_servers)
    except Exception:
        logger.exception("Failed to start Kafka anomaly producer")
        _producer = None


async def stop_kafka_producer() -> None:
    global _producer
    if _producer is not None:
        try:
            await _producer.stop()
            logger.info("Kafka anomaly producer stopped")
        except Exception:
            logger.exception("Error stopping Kafka anomaly producer")
        finally:
            _producer = None


async def publish_anomaly(agent_id: str, event: dict) -> None:
    if _producer is None:
        logger.warning("Anomaly producer not available — dropping event for agent %s", agent_id)
        return
    topic = os.getenv("KAFKA_TOPIC_ANOMALIES", "neuralops.anomalies")
    try:
        await _producer.send_and_wait(topic, key=agent_id.encode(), value=event)
    except Exception:
        logger.exception("Failed to publish anomaly event for agent %s", agent_id)
