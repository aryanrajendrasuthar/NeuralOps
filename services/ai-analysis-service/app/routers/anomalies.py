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
