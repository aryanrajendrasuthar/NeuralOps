import logging
from typing import Optional
from fastapi import APIRouter, Query, HTTPException
from pydantic import BaseModel
from datetime import datetime

logger = logging.getLogger(__name__)
router = APIRouter()


class AnomalyEvent(BaseModel):
    agent_id: str
    anomaly_score: float
    threshold: float
    is_anomaly: bool
    detected_at: datetime
    contributing_features: dict


class AnomalyStatusResponse(BaseModel):
    agent_id: str
    current_anomaly_score: float
    is_currently_anomalous: bool
    last_checked_at: Optional[datetime]
    recent_anomaly_count: int


class AnomalyInsightResponse(BaseModel):
    agent_id: str
    current_anomaly_score: float
    is_currently_anomalous: bool
    recent_anomaly_count: int
    insight: Optional[str]


@router.get("/{agent_id}/status", response_model=AnomalyStatusResponse)
async def get_anomaly_status(agent_id: str) -> AnomalyStatusResponse:
    from app.services.anomaly_detector import get_agent_status

    status = get_agent_status(agent_id)
    if status is None:
        raise HTTPException(
            status_code=404,
            detail=f"No anomaly data found for agent '{agent_id}'. "
                   "The agent must have submitted at least one trace event.",
        )
    return status


@router.get("/{agent_id}/history", response_model=list[AnomalyEvent])
async def get_anomaly_history(
    agent_id: str,
    limit: int = Query(default=50, ge=1, le=500),
    since: Optional[datetime] = None,
) -> list[AnomalyEvent]:
    from app.services.anomaly_detector import get_agent_history

    return get_agent_history(agent_id, limit=limit, since=since)


@router.get("/{agent_id}/insight", response_model=AnomalyInsightResponse)
async def get_anomaly_insight(agent_id: str) -> AnomalyInsightResponse:
    from app.services.anomaly_detector import get_agent_status, get_agent_history
    from app.services.ollama_client import generate_anomaly_insight

    status = get_agent_status(agent_id)
    if status is None:
        raise HTTPException(
            status_code=404,
            detail=f"No anomaly data found for agent '{agent_id}'.",
        )

    recent = get_agent_history(agent_id, limit=1)
    latency_ms = 0.0
    is_error = False
    if recent:
        features = recent[0].get("contributing_features", {})
        latency_ms = float(features.get("latency_ms", 0))
        is_error = bool(features.get("is_error", False))

    insight_text = None
    if status["is_currently_anomalous"]:
        insight_text = await generate_anomaly_insight(
            agent_id=agent_id,
            anomaly_score=status["current_anomaly_score"],
            latency_ms=latency_ms,
            is_error=is_error,
            recent_anomaly_count=status["recent_anomaly_count"],
        )

    return AnomalyInsightResponse(
        agent_id=agent_id,
        current_anomaly_score=status["current_anomaly_score"],
        is_currently_anomalous=status["is_currently_anomalous"],
        recent_anomaly_count=status["recent_anomaly_count"],
        insight=insight_text,
    )
