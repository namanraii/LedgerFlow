import asyncio
import json
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from consumer import alert_store, RabbitMQConsumerThread

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("notification-service")

consumer_thread = RabbitMQConsumerThread(alert_store)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting RabbitMQ consumer thread...")
    consumer_thread.start()
    yield
    logger.info("Stopping RabbitMQ consumer thread...")
    consumer_thread.stop()

app = FastAPI(title="LedgerFlow Notification Service", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/api/v1/alerts")
def get_alerts():
    return {"alerts": alert_store.get_all()}

@app.get("/api/v1/alerts/stream")
async def stream_alerts():
    async def event_generator():
        queue = asyncio.Queue()
        def callback(alert):
            queue.put_nowait(alert)

        alert_store.subscribe(callback)
        try:
            while True:
                try:
                    alert = await asyncio.wait_for(queue.get(), timeout=15.0)
                    yield f"data: {json.dumps(alert)}\n\n"
                except asyncio.TimeoutError:
                    yield ": keep-alive\n\n"
        finally:
            alert_store.unsubscribe(callback)

    return StreamingResponse(event_generator(), media_type="text/event-stream")

@app.get("/api/v1/health")
def health_check():
    return {
        "status": "UP",
        "service": "notification-service",
        "version": "1.0.0",
        "total_buffered_alerts": len(alert_store.get_all())
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8003)
