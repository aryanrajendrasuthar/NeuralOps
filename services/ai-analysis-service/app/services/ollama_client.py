import logging
import os
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.1:8b")


async def check_ollama_health() -> bool:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            return response.status_code == 200
    except Exception:
        logger.warning("Ollama health check failed — service may be starting up")
        return False


async def generate_anomaly_insight(agent_id: str, anomaly_score: float,
                                   latency_ms: float, is_error: bool,
                                   recent_anomaly_count: int) -> Optional[str]:
    prompt = (
        f"You are an AI observability assistant analyzing performance anomalies.\n\n"
        f"An anomaly was detected for AI agent '{agent_id}':\n"
        f"- Anomaly score: {anomaly_score:.4f} (more negative = more anomalous)\n"
        f"- Latency: {latency_ms:.0f}ms\n"
        f"- Is error trace: {is_error}\n"
        f"- Recent anomalies in last hour: {recent_anomaly_count}\n\n"
        f"In 2-3 sentences, explain what this likely indicates and what an engineer should check first."
    )

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{OLLAMA_BASE_URL}/api/generate",
                json={
                    "model": OLLAMA_MODEL,
                    "prompt": prompt,
                    "stream": False,
                    "options": {"temperature": 0.3, "num_predict": 150},
                },
            )
            response.raise_for_status()
            data = response.json()
            return data.get("response", "").strip()
    except Exception:
        logger.exception("Failed to generate anomaly insight from Ollama for agent %s", agent_id)
        return None
