# AccessGraph

**Accessibility-aware TTC route planning — CSC207, University of Toronto, Fall 2026**

AccessGraph is a planned Java desktop application that helps TTC passengers find routes that meet their mobility needs. It models transit connections as a graph, excludes connections that violate a passenger's accessibility requirements, and recalculates routes when a disruption makes a connection unavailable.

**Team:** Hayk Danghyan, Dean Wu, Hongbo Wang, and Adrian Lau — Group 17.

## Run the map demo

Build with Java 11 or newer and Maven 3.6.3 or newer. Run the app on a graphical desktop with internet access for OpenStreetMap tiles.

```sh
mvn clean package
java -jar target/accessgraph.jar
```

1. Sign in with username `demo` and password `accessgraph`. These public credentials are for the local demo account.
2. The map opens over downtown Toronto. In the browser, select **Start sharing location** and allow the permission prompt. Return to AccessGraph to see your position at street-level zoom. You can explore the map if you skip or deny permission.
3. Drag to pan, scroll over the map to zoom around the pointer, or use **+** and **−**. With keyboard focus on the map, use the arrow keys to pan. **My location** recenters on a fresh fix or reopens the permission page.
4. Choose **Stop sharing** to hide your live position. **Sign out** returns to the login page and ends the location session.

The demo covers sign-in and the map. Routing, accessibility profiles, disruptions, and saved trips are part of the course specification below.

### Map and location data

The app requests only tiles for the current view and caches them in `~/.accessgraph/tiles` according to OpenStreetMap's HTTP headers. The tile requests identify the displayed area. Exact device coordinates stay in memory and the app draws the position marker locally. A location fix expires after 60 seconds. [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/)

The permission helper runs on a loopback port. It calls the browser's location service only after you select **Start sharing location**. The browser and operating system control the permission prompt and may use their own location providers. The desktop app needs no API key or billing account.

### Station accessibility evidence and coverage

The bundled snapshot was checked on **November 9, 2026** and contains **109 station locations: 103 affirmative listings, 4 limited listings, and 2 unknown/conflicting listings**. Coordinates and station identifiers come from [TTC's merged GTFS dataset](https://open.toronto.ca/dataset/merged-gtfs-ttc-routes-and-schedules/), feed `S1000544` (November 1–December 19, 2026). This is a snapshot, not live station status.

Accessibility comes from each station's official TTC page, whose URL and check date are retained in every catalogue row. The downloaded GTFS marks **all 114 parent station records** with `wheelchair_boarding=1`, including known incomplete/limited stations, so that field is not used as accessibility evidence. Only a TTC “Accessible” station feature or affirmative overview qualifies. Conflicting details remain unknown: College and Lawrence currently have an accessible badge alongside an overview saying “No”, so neither is shown on the demo map. Islington, King, Museum, and Old Mill have limited listings. These conservative classifications describe the source records, not an independent accessibility audit.

Station pages are paired to GTFS parents through their boarding stop codes. Five unmatched retired Line 3 locations (Ellesmere, Lawrence East, McCowan, Midland, and Scarborough Centre) are omitted. The TTC GTFS coordinates identify station locations. The TTC station details pages provide entrance information where available. Elevator outages, partial platform access, construction, and other disability-specific requirements can change whether a journey is usable. Check official TTC station details and elevator alerts for current information.

To regenerate the snapshot, use Python **3.9+** (standard library only), then rebuild:

```sh
python3 scripts/update_station_catalogue.py
python3 -m unittest discover -s scripts -p 'test_*.py'
mvn clean package
```

The updater downloads the official GTFS archive (currently about 68 MB), the TTC station directory, and matching station pages. Failed downloads or an unexpectedly small catalogue abort before replacing the shipped data. Optional `--gtfs-zip`, `--directory-html`, and `--cache-dir` arguments support repeatable local inspection; cached pages retain their original check dates. Resource files are in `src/main/resources/data/`. Coordinate data is attributed to the City of Toronto / TTC under the [Open Government Licence – Toronto](https://open.toronto.ca/open-data-license/).

### Code organization

| Package | Responsibility |
| --- | --- |
| `entity` | TTC station records and timestamped device positions. |
| `use_case.sign_in` | Sign-in rules and the authentication boundary. |
| `use_case.station_map` | Map-session rules, viewport geometry, and the location boundary. |
| `interface_adapter` | Location controller and browser and map-tile ports. |
| `data_access` | Demo account, OpenStreetMap tiles and cache, station reader, and browser permission bridge. |
| `view` | Swing sign-in and map screens. The map view renders fetched imagery and handles gestures. |
| `app` | Dependency injection and desktop startup. |

The use cases know neither Swing nor the providers. `AppFactory` injects the concrete adapters, and the Swing map view keeps decoded tiles in memory and loads missing tiles off the event dispatch thread. The local demo authenticator can be replaced behind the same boundary when account management is in scope.

## Project specification

### Purpose and intended users

The application is intended for TTC passengers who need step-free or wheelchair-accessible journeys, or who want to limit transfers and walking. A route should be recommended only when the available data supports its compatibility with the selected requirements.

The first version will cover a manageable, explicitly documented subset of the TTC network. It will focus on station-to-station travel and relevant entrance, platform, and transfer connections where supporting data is available.

### Core behaviour

- Let passengers select an origin, destination, and accessibility profile through a Swing interface.
- Represent travel and transfer connections using a weighted graph with nonnegative estimated travel times.
- Use Dijkstra's algorithm to find a feasible route, tracking any additional state needed to enforce constraints such as a transfer limit.
- Treat accessibility restrictions as hard constraints: a connection requiring stairs must be excluded when stairs are disallowed.
- Distinguish confirmed accessibility from unknown accessibility. Do not silently treat missing information as accessible.
- Display the route's station sequence, estimated duration, transfer count, and relevant accessibility explanations.
- Apply supported disruptions to the affected connections, then recalculate the selected journey.
- Save accessibility preferences and frequent journeys locally so they survive an application restart.

Travel times may initially be estimates rather than live arrival predictions. Results must identify their data source and whether live, cached, or simulated conditions are being used. The application is a course prototype; it does not provide verified end-to-end navigation guarantees.

## User stories and acceptance criteria

Each teammate will lead at least one complete use case across its application layers. Owners are **to be agreed by the team**; the order below does not assign work to particular members.

### 1. Accessible route planning

> As a TTC passenger, I want to find a route between two stations that meets my accessibility requirements so that I can avoid barriers I cannot navigate.

**Owner:** Adrian Lau. MVP implemented: choose two stations in the panel beside the map and select **Find route** (step-free profile, at most 2 transfers, Lines 1, 2 and 4).

Acceptance criteria:

- A passenger can select two supported stations and search using their current accessibility profile.
- Every returned route satisfies the supported hard constraints and uses only available connections.
- Results show the station sequence, estimated duration, transfer count, and accessibility information.
- Invalid selections, unsupported stations, and searches with no feasible route produce clear messages.

### 2. Accessibility profile management

> As a passenger with mobility restrictions, I want to save my accessibility preferences so that future searches automatically consider my needs.

**Owner:** Hayk Danghyan. MVP implemented: open the **Profile** tab beside the map, choose whether you use a wheelchair, whether stairs and escalators are OK and how many transfers you accept, and select **Save profile**. The profile is saved to `~/.accessgraph/profile.properties` and restored at the next start. Until a profile is saved, or when the saved file cannot be read, the app explains why, opens the Profile tab and searches with the strictest profile (wheelchair, at most 2 transfers). The station data has no escalator information, so a passenger who cannot use stairs is limited to stations the TTC confirms as accessible even when escalators are OK. The tab says so.

Acceptance criteria:

- A passenger can configure wheelchair-accessibility requirements, whether stairs and escalators are permitted, and a maximum transfer count.
- The profile is saved locally and restored after restarting the application.
- Subsequent searches use the saved preferences unless the passenger changes them.
- Missing or invalid saved data is reported clearly and does not crash the application; the passenger can confirm a new profile before searching.

### 3. Disruption-aware rerouting

> As a TTC passenger, I want my route to be recalculated when a required connection becomes inaccessible so that I can find a feasible alternative.

**Owner:** Dean Wu. MVP implemented: after finding a route, open the **Disruptions** tab beside the map, choose a line, a connection and a type, and select **Simulate disruption**. **Restore selected** makes the connection available again. Disruptions are simulated and last until the application closes.

Acceptance criteria:

- A passenger can simulate an outage or closure on a supported connection and restore it afterwards.
- The routing graph reflects the disruption, and recalculation excludes the unavailable connection.
- The application explains why the previous route became invalid and displays a replacement or reports that none exists.
- Live disruption retrieval will be added if feed access and connection mapping are validated. Simulation remains available for a repeatable offline demonstration.

### 4. Saved trips and route comparison

> As a frequent TTC passenger, I want to save common journeys and compare their routes under different conditions so that I can quickly choose a suitable journey.

**Owner:** Hongbo Wang.

Acceptance criteria:

- A passenger can name, save, load, and delete an origin–destination pair.
- Saved trips remain available after restarting the application.
- Loading a trip runs a new search using the current accessibility profile and network conditions rather than assuming an old route remains valid.
- When a disruption changes a route, the passenger can compare the previous and recalculated station sequences, estimated durations, and transfer counts. The previous route is clearly labelled if it is no longer feasible.

## Data sources and external API plan

The following sources support either the station map or planned routing integrations:

| Source | Intended use | Validation needed |
| --- | --- | --- |
| [Toronto Open Data: TTC GTFS](https://open.toronto.ca/dataset/merged-gtfs-ttc-routes-and-schedules/) | Station-map coordinates and identifiers; later, travel connections. | Inspected feed `S1000544`; parent accessibility flags are unsuitable on their own. Routing graph still to implement. |
| [OpenStreetMap raster tiles](https://operations.osmfoundation.org/policies/tiles/) | Keyless basemap with locally rendered station pins. | Live TTC overview verified. Local fixtures cover cache reuse, revalidation, and marker rendering. |
| Official TTC station pages | Station-level wheelchair-accessibility evidence and source links. | Dated snapshot checked November 9, 2026; two contradictory records remain unknown. No live elevator assessment. |
| [Toronto Open Data: TTC GTFS-Realtime](https://open.toronto.ca/dataset/ttc-gtfs-realtime-gtfs-rt) | Candidate external API for service and accessibility disruptions. | Verify endpoint access from Java, authentication, format, alert coverage, and mapping to specific connections. |
| [TTC elevators and escalators](https://www.ttc.ca/accessibility/Easier-access-on-the-TTC/Elevators-and-Escalators) | Reference for accessibility information and elevator locations or status. | Determine what information can be reliably incorporated and attributed. |
| [GTFS accessibility documentation](https://gtfs.org/getting-started/features/accessibility/) | Interpret accessibility-related fields. | Check which documented fields the selected TTC data actually supplies. |

The MVP must include at least one verified, working external API integration. Downloaded static files and offline HTTP fixtures alone do not complete that requirement. Launch the demo online and confirm the Toronto map renders from the live tile service. The proposed TTC realtime source still requires validation for disruption data.

An elevator outage must affect only the connections supported by the data, rather than automatically disabling an entire station. Ambiguous alerts must be identified as such. Deterministic fixtures and clearly labelled simulated conditions will support testing and demonstrations when a live feed is unavailable.

## Technical design

- **Platform:** Java 11 and Swing, built with Maven.
- **Architecture:** Clean Architecture and SOLID principles, with constructor-based dependency injection.
- **Domain:** Stations and network nodes, transit connections, routes, accessibility profiles, disruptions, and saved trips.
- **Use cases:** Interactors with input/output boundaries; controllers, presenters, and view models connect them to Swing.
- **Data access:** Gateway interfaces separate network retrieval and persistence from application logic. Concrete adapters depend on those interfaces.
- **Persistence:** Local JSON files are the initial proposal for profiles and saved trips; the implementation and library choice remain to be agreed.
- **Testing:** JUnit tests for routing, constraints, interactors, disruption updates, and persistence behaviour. Domain logic must be testable without Swing or live network requests.

Routing and use-case logic must not depend on Swing, API response formats, or storage libraries. The exact classes and package structure will be refined during implementation.

## Scope

### Minimum viable product

- A functional Swing route planner for a documented subset of the TTC network.
- Constraint-aware graph routing and clear handling of unavailable routes.
- Persistent profiles and saved journeys.
- Connection-level disruption simulation, rerouting, and before/after comparison.
- One verified, working external API integration.
- Four complete use cases with meaningful contributions from all four teammates.
- Unit tests, design documentation, and a repeatable demonstration.

### Possible extensions

Full-network coverage, live elevator outage integration if not feasible for the MVP, multiple alternative paths for the same conditions, schedule-aware arrival predictions, precise walking distances, a schematic network visualization, and disruption impact analysis.

### Out of scope for the initial version

Web or mobile clients, user accounts, payments, a complex backend, machine learning, and street-level geographic navigation.

## Collaboration

Work will proceed through issues, feature branches, implementation and tests, pull requests, code review, and merges. Each teammate should lead a complete use case and contribute meaningful code, while reviewing and integrating work with the rest of the team. Shared expectations are recorded in [TeamContract.md](TeamContract.md).

## Verification

```sh
mvn test
python3 -m unittest discover -s scripts -p 'test_*.py'
node --test scripts/test_location_bridge.cjs
```

The Java tests cover sign-in, the Toronto fallback, map gestures, location updates, and permission denial. Map and browser bridge tests use loopback fixtures. Browser and OS permission prompts depend on the device.

## Course references

- [CSC207 project overview](https://q.utoronto.ca/courses/444184/pages/project-overview)
- [CSC207 project expectations](https://q.utoronto.ca/courses/444184/pages/project-expectations)
- [Developing project user stories](https://q.utoronto.ca/courses/444184/pages/developing-project-user-stories)

This specification should be updated as the team confirms ownership, validates data sources, and completes functionality.
