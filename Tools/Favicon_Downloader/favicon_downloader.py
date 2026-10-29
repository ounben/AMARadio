import os
import sqlite3
import threading
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from PIL import Image
import resvg_py

DB_PATH = r"C:\Users\bou\StudioProjects\AMARadio\app\src\main\assets\databases\radio_browser_database.db"
OUTPUT_DIR = "favicons"
MAX_WORKERS = 40
TIMEOUT = 5
TARGET_SIZE = (256, 256)
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

# Thread-sichere Zähler
lock = threading.Lock()
stats = {
    "total_db": 0,
    "no_url_in_db": 0,
    "download_success": 0,
    "already_exists": 0,
    "failed": 0,
    "processed": 0
}


def is_svg_payload(data: bytes) -> bool:
    snippet = data[:100].lstrip().lower()
    return snippet.startswith(b"<svg") or (
        snippet.startswith(b"<?xml") and b"<svg" in data[:1024].lower()
    )


def process_favicon(station_uuid: str, url: str) -> None:
    global stats

    prefix = station_uuid[:2].lower()
    target_dir = os.path.join(OUTPUT_DIR, prefix)
    target_file = os.path.join(target_dir, f"{station_uuid}.jpg")

    if os.path.exists(target_file):
        with lock:
            stats["already_exists"] += 1
            stats["processed"] += 1
            _print_progress()
        return

    success = False
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            data = response.read()

        if data:
            if url.lower().endswith(".svg") or is_svg_payload(data):
                svg_string = data.decode("utf-8", errors="ignore")
                png_bytes = resvg_py.svg_to_bytes(svg_string)
                img = Image.open(BytesIO(png_bytes))
            else:
                img = Image.open(BytesIO(data))

            with img:
                if img.mode in ("RGBA", "LA", "P"):
                    img = img.convert("RGBA")
                    bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
                    img = Image.alpha_composite(bg, img)

                img_rgb = img.convert("RGB")
                resized = img_rgb.resize(TARGET_SIZE, Image.Resampling.LANCZOS)

                os.makedirs(target_dir, exist_ok=True)
                resized.save(target_file, "JPEG", quality=85)
                success = True

    except Exception:
        success = False

    with lock:
        if success:
            stats["download_success"] += 1
        else:
            stats["failed"] += 1
        stats["processed"] += 1
        _print_progress()


def _print_progress():
    p = stats["processed"]
    if p % 1000 == 0:
        print(f"Fortschritt: {p} URLs verarbeitet...")


def main():
    if not os.path.isfile(DB_PATH):
        print(f"Fehler: Datenbank nicht gefunden: {DB_PATH}")
        return

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Gesamtzahl aller Sender
    cursor.execute("SELECT COUNT(*) FROM Station")
    total_stations = cursor.fetchone()[0]

    # Sender mit gültiger Favicon-URL abfragen
    query = """
        SELECT StationUuid, Favicon 
        FROM Station 
        WHERE Favicon IS NOT NULL 
          AND Favicon != '' 
          AND StationUuid IS NOT NULL
    """
    cursor.execute(query)
    rows = cursor.fetchall()
    conn.close()

    stats["total_db"] = total_stations
    stats["no_url_in_db"] = total_stations - len(rows)

    print(f"Gesamte Sender in DB:       {stats['total_db']}")
    print(f"Ohne Favicon-URL in DB:     {stats['no_url_in_db']}")
    print(f"Mit Favicon-URL (zu prüfen): {len(rows)}")
    print("-" * 40)

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        for station_uuid, favicon_url in rows:
            executor.submit(process_favicon, str(station_uuid), str(favicon_url))

    print("\n" + "=" * 40)
    print("ERGEBNIS:")
    print(f"Sender insgesamt:               {stats['total_db']}")
    print(f"Keine URL in DB hinterlegt:     {stats['no_url_in_db']}")
    print(f"Bereits vorhanden gewesen:      {stats['already_exists']}")
    print(f"Erfolgreich neu geladen:        {stats['download_success']}")
    print(f"Fehlgeschlagen (404/Timeout):   {stats['failed']}")
    print("=" * 40)


if __name__ == "__main__":
    main()