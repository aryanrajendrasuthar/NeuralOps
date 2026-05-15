import logging
import os
from collections import defaultdict, deque
from datetime import datetime, timezone
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)

WINDOW_SIZE = int(os.getenv("ANOMALY_WINDOW_SIZE", "1000"))
ANOMALY_THRESHOLD = float(os.getenv("ANOMALY_THRESHOLD", "-0.1"))
MIN_SAMPLES_FOR_SCORING = int(os.getenv("ANOMALY_MIN_SAMPLES", "50"))

_agent_buffers: dict[str, deque] = defaultdict(lambda: deque(maxlen=WINDOW_SIZE))
_agent_models: dict[str, object] = {}
_agent_anomaly_history: dict[str, list] = defaultdict(list)
_agent_current_scores: dict[str, float] = {}
_agent_last_checked: dict[str, datetime] = {}


async def process_trace_event(event: dict) -> None:
    agent_id = event.get("agentId")
    latency_ms = event.get("latencyMs")
    trace_type = event.get("traceType", "")

    if not agent_id or latency_ms is None:
        return

    is_error = trace_type == "ERROR"
    feature_vector = [float(latency_ms), 1.0 if is_error else 0.0]
    _agent_buffers[agent_id].append(feature_vector)

    buffer = list(_agent_buffers[agent_id])
    if len(buffer) < MIN_SAMPLES_FOR_SCORING:
        return

    score = _score_event(agent_id, buffer, feature_vector)
    _agent_current_scores[agent_id] = score
    _agent_last_checked[agent_id] = datetime.now(timezone.utc)

    if score < ANOMALY_THRESHOLD:
        anomaly_event = {
            "agent_id": agent_id,
            "anomaly_score": score,
            "threshold": ANOMALY_THRESHOLD,
            "is_anomaly": True,
            "detected_at": datetime.now(timezone.utc),
            "contributing_features": {
                "latency_ms": latency_ms,
                "is_error": is_error,
            },
        }
        _agent_anomaly_history[agent_id].append(anomaly_event)
        await _publish_anomaly(anomaly_event)
        logger.info("Anomaly detected for agent %s: score=%.4f", agent_id, score)


def _score_event(agent_id: str, buffer: list, feature_vector: list) -> float:
    try:
        from sklearn.ensemble import IsolationForest

        if agent_id not in _agent_models or len(buffer) % 100 == 0:
            X = np.array(buffer)
            model = IsolationForest(
                n_estimators=100,
                contamination=0.05,
                random_state=42,
                n_jobs=-1,
            )
            model.fit(X)
            _agent_models[agent_id] = model

        model = _agent_models[agent_id]
        score = model.score_samples([feature_vector])[0]
        return float(score)
    except Exception:
        logger.exception("Failed to score event for agent %s", agent_id)
        return 0.0


async def _publish_anomaly(event: dict) -> None:
    from app.kafka.producer import publish_anomaly
    await publish_anomaly(event["agent_id"], event)


def get_agent_status(agent_id: str) -> Optional[dict]:
    if agent_id not in _agent_current_scores:
        return None

    score = _agent_current_scores[agent_id]
    recent_anomalies = [
        a for a in _agent_anomaly_history.get(agent_id, [])
        if (datetime.now(timezone.utc) - a["detected_at"]).total_seconds() < 3600
    ]

    return {
        "agent_id": agent_id,
        "current_anomaly_score": score,
        "is_currently_anomalous": score < ANOMALY_THRESHOLD,
        "last_checked_at": _agent_last_checked.get(agent_id),
        "recent_anomaly_count": len(recent_anomalies),
    }


def get_agent_history(agent_id: str, limit: int = 50, since=None) -> list:
    history = _agent_anomaly_history.get(agent_id, [])
    if since:
        history = [h for h in history if h["detected_at"] >= since]
    return list(reversed(history))[-limit:]
