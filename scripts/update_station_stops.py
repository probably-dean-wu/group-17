#!/usr/bin/env python3
"""List the GTFS boarding stops of every catalogued station (stdlib only).

TTC realtime alerts name platforms by their GTFS stop id, while the station catalogue uses the id of the
parent station. This table lets the application match an alert to the stations it affects.
"""
import argparse
import csv
import io
import json
from pathlib import Path
import tempfile
import zipfile

from update_station_catalogue import METADATA, fetch


def station_stops(stops, station_ids):
    """Return (stop_id, station_id) for each boarding stop whose parent station is catalogued."""
    rows = [(row["stop_id"], row["parent_station"]) for row in stops
            if row["location_type"] == "0" and row["parent_station"] in station_ids]
    return sorted(rows, key=lambda row: (row[1], row[0]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gtfs-zip", type=Path, help="Use an already downloaded official GTFS ZIP")
    parser.add_argument("--data-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "src/main/resources/data")
    args = parser.parse_args()
    catalogue = (args.data_dir / "ttc-stations.tsv").read_text(encoding="utf-8").splitlines()[1:]
    station_ids = {row.split("\t")[0] for row in catalogue}
    with tempfile.TemporaryDirectory(prefix="accessgraph-ttc-") as temporary:
        archive = args.gtfs_zip
        if archive is None:
            metadata = json.loads(fetch(METADATA))
            resource = next(r for r in metadata["result"]["resources"] if r["format"] == "ZIP")
            archive = Path(temporary) / "gtfs.zip"
            archive.write_bytes(fetch(resource["url"]))
        with zipfile.ZipFile(archive) as gtfs:
            stops = list(csv.DictReader(io.TextIOWrapper(gtfs.open("stops.txt"), encoding="utf-8-sig")))
    rows = station_stops(stops, station_ids)
    missing = station_ids - {station for _, station in rows}
    if missing:
        raise RuntimeError("Stations without a boarding stop; existing file left unchanged: "
                           + ", ".join(sorted(missing)))
    lines = ["stop_id\tstation_id"]
    lines.extend("\t".join(row) for row in rows)
    (args.data_dir / "ttc-station-stops.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Wrote", len(rows), "boarding stops for", len(station_ids), "stations")


if __name__ == "__main__":
    main()
