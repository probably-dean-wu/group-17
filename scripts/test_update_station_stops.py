"""Checks for the stop-to-station table; no network requests."""
import unittest
from update_station_stops import station_stops


def stop(stop_id, location_type, parent):
    return {"stop_id": stop_id, "location_type": location_type, "parent_station": parent}


class StationStopsTest(unittest.TestCase):
    def test_keeps_boarding_stops_of_catalogued_stations(self):
        stops = [stop("200", "0", "9"), stop("100", "0", "9"), stop("300", "0", "7")]
        self.assertEqual([("300", "7"), ("100", "9"), ("200", "9")], station_stops(stops, {"7", "9"}))

    def test_skips_entrances_parents_and_uncatalogued_stations(self):
        stops = [stop("9", "1", ""), stop("400", "2", "9"), stop("500", "3", "9"),
                 stop("600", "0", "8"), stop("700", "0", "")]
        self.assertEqual([], station_stops(stops, {"9"}))


if __name__ == "__main__":
    unittest.main()
