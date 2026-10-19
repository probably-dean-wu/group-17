# AccessGraph

**Accessibility-aware TTC route planning — CSC207, University of Toronto, Fall 2026**

AccessGraph is a planned Java desktop application that helps TTC passengers find routes that meet their mobility needs. It models transit connections as a graph, excludes connections that violate a passenger's accessibility requirements, and recalculates routes when a disruption makes a connection unavailable.

**Team:** Hayk Danghyan, Dean Wu, Hongbo Wang, and Adrian Lau — Group 17.

**Project status:** Specification and planning. The features below describe intended behaviour, not completed functionality. The repository currently contains a Maven configuration targeting Java 11 and the team contract. Story ownership and data integration details still require team agreement.

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

**Owner:** To be assigned.

Acceptance criteria:

- A passenger can select two supported stations and search using their current accessibility profile.
- Every returned route satisfies the supported hard constraints and uses only available connections.
- Results show the station sequence, estimated duration, transfer count, and accessibility information.
- Invalid selections, unsupported stations, and searches with no feasible route produce clear messages.

### 2. Accessibility profile management

> As a passenger with mobility restrictions, I want to save my accessibility preferences so that future searches automatically consider my needs.

**Owner:** To be assigned.

Acceptance criteria:

- A passenger can configure wheelchair-accessibility requirements, whether stairs and escalators are permitted, and a maximum transfer count.
- The profile is saved locally and restored after restarting the application.
- Subsequent searches use the saved preferences unless the passenger changes them.
- Missing or invalid saved data is reported clearly and does not crash the application; the passenger can confirm a new profile before searching.

### 3. Disruption-aware rerouting

> As a TTC passenger, I want my route to be recalculated when a required connection becomes inaccessible so that I can find a feasible alternative.

**Owner:** To be assigned.

Acceptance criteria:

- A passenger can simulate an outage or closure on a supported connection and restore it afterwards.
- The routing graph reflects the disruption, and recalculation excludes the unavailable connection.
- The application explains why the previous route became invalid and displays a replacement or reports that none exists.
- Live disruption retrieval will be added if feed access and connection mapping are validated. Simulation remains available for a repeatable offline demonstration.

### 4. Saved trips and route comparison

> As a frequent TTC passenger, I want to save common journeys and compare their routes under different conditions so that I can quickly choose a suitable journey.

**Owner:** To be assigned.

Acceptance criteria:

- A passenger can name, save, load, and delete an origin–destination pair.
- Saved trips remain available after restarting the application.
- Loading a trip runs a new search using the current accessibility profile and network conditions rather than assuming an old route remains valid.
- When a disruption changes a route, the passenger can compare the previous and recalculated station sequences, estimated durations, and transfer counts. The previous route is clearly labelled if it is no longer feasible.

## Data sources and external API plan

The following are proposed sources, pending inspection of their actual feeds, access requirements, licensing, and available fields:

| Source | Intended use | Validation needed |
| --- | --- | --- |
| [Toronto Open Data: TTC GTFS](https://open.toronto.ca/dataset/merged-gtfs-ttc-routes-and-schedules/) | Establish supported stops, routes, and travel connections. | Confirm usable files, identifiers, accessibility fields, and the initial network subset. |
| [Toronto Open Data: TTC GTFS-Realtime](https://open.toronto.ca/dataset/ttc-gtfs-realtime-gtfs-rt) | Candidate external API for service and accessibility disruptions. | Verify endpoint access from Java, authentication, format, alert coverage, and mapping to specific connections. |
| [TTC elevators and escalators](https://www.ttc.ca/accessibility/Easier-access-on-the-TTC/Elevators-and-Escalators) | Reference for accessibility information and elevator locations or status. | Determine what information can be reliably incorporated and attributed. |
| [GTFS accessibility documentation](https://gtfs.org/getting-started/features/accessibility/) | Interpret accessibility-related fields. | Check which documented fields the selected TTC data actually supplies. |

The MVP must include at least one working external API integration. Downloaded static files alone will not be treated as completing that integration. If the proposed realtime source is unsuitable, the team must select another usable API before finalizing the implementation.

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

## Running the application and demonstrations

Application entry points, dependencies, build/run instructions, and screenshots will be added as working features are implemented. No runnable application or completed feature demonstration is documented yet.

## Course references

- [CSC207 project overview](https://q.utoronto.ca/courses/444184/pages/project-overview)
- [CSC207 project expectations](https://q.utoronto.ca/courses/444184/pages/project-expectations)
- [Developing project user stories](https://q.utoronto.ca/courses/444184/pages/developing-project-user-stories)

This specification should be updated as the team confirms ownership, validates data sources, and completes functionality.
