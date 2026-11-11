#!/usr/bin/env python3
"""Build a dated TTC station snapshot from official GTFS and station pages (stdlib only)."""
import argparse
import concurrent.futures
import csv
import difflib
import html.parser
import io
import json
from pathlib import Path
import re
import tempfile
from datetime import datetime, timezone
import unicodedata
import urllib.request
import zipfile

METADATA = ("https://ckan0.cf.opendata.inter.prod-toronto.ca/api/3/action/"
            "package_show?id=merged-gtfs-ttc-routes-and-schedules")
DIRECTORY = "https://www.ttc.ca/routes-and-schedules"


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "AccessGraph-CSC207/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


class DirectoryParser(html.parser.HTMLParser):
    stations = None

    def handle_starttag(self, tag, attrs):
        for _, value in attrs:
            if value and "transitapiendpointaddress" in value:
                self.stations = json.loads(value)["stations"]


class StationParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.cell = None
        self.row = []
        self.overview = ""
        self.feature = None
        self.accessible_badge = False

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        if tag in ("th", "td"):
            self.cell = []
        if tag == "span" and "subway-station-features-name" in dict(attrs).get("class", ""):
            self.feature = []

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)
        if self.feature is not None:
            self.feature.append(data)

    def handle_endtag(self, tag):
        if tag in ("th", "td") and self.cell is not None:
            self.row.append(" ".join(" ".join(self.cell).split()))
            self.cell = None
        if tag == "tr" and len(self.row) >= 2 and self.row[0].casefold() == "accessible":
            self.overview = self.row[1]
        if tag == "span" and self.feature is not None:
            self.accessible_badge |= " ".join(self.feature).strip().casefold() == "accessible"
            self.feature = None

    def status(self):
        value = self.overview.casefold()
        limited = value.startswith("no") or "not accessible" in value or "street level only" in value
        if limited:
            return "UNKNOWN" if self.accessible_badge else "LIMITED"
        if value.startswith("yes") or self.accessible_badge:
            return "CONFIRMED"
        return "UNKNOWN"


def normalized(value):
    return re.sub(r"[^a-z0-9]", "", unicodedata.normalize("NFKD", value.casefold()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gtfs-zip", type=Path, help="Use an already downloaded official GTFS ZIP")
    parser.add_argument("--directory-html", type=Path, help="Use an already downloaded official directory")
    parser.add_argument("--cache-dir", type=Path, help="Reuse station HTML; retain each file's check date")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "src/main/resources/data")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="accessgraph-ttc-") as temporary:
        cache = args.cache_dir or Path(temporary)
        cache.mkdir(parents=True, exist_ok=True)
        archive = args.gtfs_zip
        if archive is None:
            metadata = json.loads(fetch(METADATA))
            resource = next(r for r in metadata["result"]["resources"] if r["format"] == "ZIP")
            archive = Path(temporary) / "gtfs.zip"
            archive.write_bytes(fetch(resource["url"]))
        with zipfile.ZipFile(archive) as gtfs:
            stops = list(csv.DictReader(io.TextIOWrapper(gtfs.open("stops.txt"), encoding="utf-8-sig")))
            feed = next(csv.DictReader(io.TextIOWrapper(gtfs.open("feed_info.txt"), encoding="utf-8-sig")))
        parents = {row["stop_id"]: row for row in stops if row["location_type"] == "1"}
        parent_codes = {}
        for row in stops:
            if row["location_type"] == "0" and row["parent_station"] in parents:
                parent_codes.setdefault(row["stop_code"], set()).add(row["parent_station"])
        directory = DirectoryParser()
        directory.feed(args.directory_html.read_text() if args.directory_html else fetch(DIRECTORY).decode())
        if not directory.stations:
            raise RuntimeError("TTC station directory could not be parsed; existing catalogue left unchanged.")
        sources = {}
        for entry in directory.stations:
            for code in entry["StopCode"].split("|"):
                for parent in parent_codes.get(code.strip(), set()):
                    sources.setdefault(parent, set()).add(entry["Url"])

        def inspect(item):
            identifier, stop = item
            candidates = sources.get(identifier, set())
            if not candidates:
                # Closed Line 3 stations have no matching directory boarding codes.
                return None
            url = max(sorted(candidates), key=lambda u: difflib.SequenceMatcher(
                None, normalized(stop["stop_name"]), normalized(u.rsplit("/", 1)[-1]
                                                               .removesuffix("-station"))).ratio())
            if not url.startswith("https://www.ttc.ca/subway-stations/"):
                raise RuntimeError("Unexpected station source host.")
            page = cache / (identifier + ".html")
            if not page.exists():
                page.write_bytes(fetch(url))
            parsed = StationParser()
            parsed.feed(page.read_text(encoding="utf-8"))
            checked = datetime.fromtimestamp(page.stat().st_mtime, timezone.utc).date().isoformat()
            return [identifier, stop["stop_name"], stop["stop_lat"], stop["stop_lon"],
                    parsed.status(), url, checked]

        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            # Any failed fetch aborts before changing the shipped snapshot.
            stations = [row for row in executor.map(inspect, parents.items()) if row]
        if len(stations) < 70:
            raise RuntimeError("Unexpectedly small catalogue; existing files left unchanged.")
        stations.sort(key=lambda row: row[1].casefold())
        lines = ["id\tname\tlatitude\tlongitude\taccessibility\tsource_url\tchecked_on"]
        lines.extend("\t".join(row) for row in stations)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / "ttc-stations.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
        (args.output_dir / "ttc-stations.properties").write_text(
            "feed.version=" + feed["feed_version"] + "\nfeed.start=" + feed["feed_start_date"]
            + "\nfeed.end=" + feed["feed_end_date"] + "\n", encoding="utf-8")
        print("Wrote", len(stations), "stations:",
              {status: sum(row[4] == status for row in stations)
               for status in ("CONFIRMED", "LIMITED", "UNKNOWN")})
        print("Excluded unmatched GTFS parents:",
              ", ".join(stop["stop_name"] for key, stop in parents.items() if key not in sources))


if __name__ == "__main__":
    main()
