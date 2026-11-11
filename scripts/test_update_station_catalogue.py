"""Regression checks for the official station-page evidence rules; no network requests."""
import unittest
from update_station_catalogue import StationParser


class StationParserTest(unittest.TestCase):
    def parse(self, markup):
        parser = StationParser()
        parser.feed(markup)
        return parser.status()

    def test_explicit_station_yes_is_confirmed(self):
        self.assertEqual("CONFIRMED", self.parse(
            '<table><tr><th>Accessible</th><td>Yes</td></tr></table>'))

    def test_street_level_access_does_not_confirm_subway_access(self):
        self.assertEqual("LIMITED", self.parse(
            '<tr><th>Accessible</th><td>Street Level only. Subway platforms are not accessible</td></tr>'))

    def test_conflicting_badge_and_overview_is_unknown(self):
        self.assertEqual("UNKNOWN", self.parse(
            '<span class="subway-station-features-name field-name">Accessible</span>'
            '<tr><th>Accessible</th><td>No</td></tr>'))

    def test_wheel_trans_icon_and_navigation_labels_are_not_station_evidence(self):
        self.assertEqual("UNKNOWN", self.parse(
            '<nav>Accessibility</nav><tr><th>Wheel Trans stop</th>'
            '<td><img alt="Accessible">Bus platform</td></tr>'))


if __name__ == "__main__":
    unittest.main()
