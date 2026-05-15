import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import make_asgi_app

from app.routers import health, anomalies
from app.kafka.consumer import start_kafka_consumer, stop_kafka_consumer
from app.kafka.producer import start_kafka_producer, stop_kafka_producer

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting NeuralOps AI Analysis Service")
    await start_kafka_producer()
    await start_kafka_consumer()
    yield
    logger.info("Shutting down NeuralOps AI Analysis Service")
    await stop_kafka_consumer()
    await stop_kafka_producer()


app = FastAPI(
    title="NeuralOps AI Analysis Service",
    description="Isolation Forest anomaly detection for AI agent trace streams",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "http://localhost:3000").split(","),
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)

app.include_router(health.router, tags=["Health"])
app.include_router(anomalies.router, prefix="/api/v1/anomalies", tags=["Anomalies"])
