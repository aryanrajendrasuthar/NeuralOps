import os
from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    kafka_connected: bool
    ollama_available: bool


@router.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    from app.kafka.consumer import is_consumer_running
    from app.services.ollama_client import check_ollama_health

    ollama_ok = await check_ollama_health()

    return HealthResponse(
        status="ok" if is_consumer_running() and ollama_ok else "degraded",
        service="neuralops-ai-analysis",
        version="1.0.0",
        kafka_connected=is_consumer_running(),
        ollama_available=ollama_ok,
    )
