import os
import asyncio
import uvicorn
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from listener import create_app
from parser import parse_map
from config import MYSEKAI_RAW_DIR, MYSEKAI_PARSED_DIR, MYSEKAI_DRAW_MAP_DIR, MYSEKAI_DRAW_OVERVIEW_DIR, MYSEKAI_PARSE_FAIL_DIR, IN_PORT, ONLY_RARE

os.environ["PJ_RES_DIR"] = os.path.join(
    os.path.dirname(__file__),
    "pjsk_mysekai_prototype"
)

from pjsk_mysekai_prototype.draw import render_and_save


os.makedirs(MYSEKAI_RAW_DIR, exist_ok=True)
os.makedirs(MYSEKAI_PARSED_DIR, exist_ok=True)
os.makedirs(MYSEKAI_DRAW_MAP_DIR, exist_ok=True)
os.makedirs(MYSEKAI_DRAW_OVERVIEW_DIR, exist_ok=True)
os.makedirs(MYSEKAI_PARSE_FAIL_DIR, exist_ok=True)


async def process_file(filepath: str):
    try:
        filename = os.path.basename(filepath)
        parts = filename.rsplit(".", 1)[0].split('_')
        game_server = parts[0]
        user_id = parts[2]

        with open(filepath, "rb") as f:
            raw_data = f.read()

        parsed_data = await asyncio.to_thread(parse_map, raw_data, game_server, user_id)
        output_map_path = os.path.join(MYSEKAI_DRAW_MAP_DIR, f"{game_server}_{user_id}.png")
        output_overview_path = os.path.join(MYSEKAI_DRAW_OVERVIEW_DIR, f"{game_server}_{user_id}.png")

        await asyncio.to_thread(render_and_save, parsed_data, output_map_path, output_overview_path, only_rare=ONLY_RARE)

        print(f"[Parser] Parsed map saved: {output_map_path}\n[Parser] Parsed overview saved: {output_overview_path}")
    except Exception as e:
        print(f"[Parser] Failed to process {filepath}: {e}")

async def worker(queue: asyncio.Queue):
    while True:
        filepath = await queue.get()
        await process_file(filepath)
        queue.task_done()

async def main():
    queue = asyncio.Queue()
    app = create_app(queue)

    config = uvicorn.Config(app, host="0.0.0.0", port=IN_PORT, log_level="info", loop="asyncio")
    server = uvicorn.Server(config)

    listener_task = asyncio.create_task(server.serve())
    worker_task = asyncio.create_task(worker(queue))

    print("Run orchestrator: listener + parser loop started")
    await asyncio.gather(listener_task, worker_task)

if __name__ == "__main__":
    asyncio.run(main())