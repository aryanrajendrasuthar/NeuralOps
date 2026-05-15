import asyncio
import json
import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

_consumer_task: Optional[asyncio.Task] = None
_consumer_running: bool = False


def is_consumer_running() -> bool:
    return _consumer_running


async def start_kafka_consumer() -> None:
    global _consumer_task, _consumer_running
    _consumer_task = asyncio.create_task(_run_consumer())
    logger.info("Kafka consumer task scheduled")


async def stop_kafka_consumer() -> None:
    global _consumer_task, _consumer_running
    _consumer_running = False
    if _consumer_task and not _consumer_task.done():
        _consumer_task.cancel()
        try:
            await _consumer_task
        except asyncio.CancelledError:
            pass
    logger.info("Kafka consumer stopped")


async def _run_consumer() -> None:
    global _consumer_running

    try:
        from aiokafka import AIOKafkaConsumer
        from app.services.anomaly_detector import process_trace_event

        bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        topic = os.getenv("KAFKA_TOPIC_TRACES_RAW", "neuralops.traces.raw")
        group_id = os.getenv("KAFKA_CONSUMER_GROUP", "neuralops-ai-analysis")

        consumer = AIOKafkaConsumer(
            topic,
            bootstrap_servers=bootstrap_servers,
            group_id=group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            value_deserializer=lambda b: json.loads(b.decode("utf-8")),
        )

        await consumer.start()
        _consumer_running = True
        logger.info("Kafka consumer connected to %s, topic: %s", bootstrap_servers, topic)

        try:
            async for message in consumer:
                try:
                    await process_trace_event(message.value)
                    await consumer.commit()
                except Exception:
                    logger.exception("Error processing trace event from partition %d offset %d",
                                     message.partition, message.offset)
        finally:
            await consumer.stop()
            _consumer_running = False

    except Exception:
        _consumer_running = False
        logger.exception("Kafka consumer failed to start — will not retry automatically")
