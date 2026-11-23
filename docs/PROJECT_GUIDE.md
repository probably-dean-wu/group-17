# AccessGraph project guide (CSC207, Group 17)

A shared checklist of what the course grades and the design rules we follow. Keep it updated in PRs.

## 1. Rubric: where the 100 marks are and how to get 5/5

| Item | Marks | What 5/5 needs |
| --- | --- | --- |
| Presentation materials | 15 | Polished slides, no typos, diagrams and screenshots that make the design easy to follow |
| Presentation delivery | 15 | Rehearsed, **under 15 minutes, every member speaks** (required for 4/5+), smooth handoffs, explain instead of reading |
| Demo | 10 | Runs end to end with data prepared in advance; shows **each member's user story**; ties back to the design slides |
| Question answering | 5 | Accurate, concise answers showing deep knowledge of our code and course concepts. Everyone must be able to explain their own code |
| Specification | 10 | User stories and use cases say **what** the program does, never **how**. A non-programmer understands the purpose |
| Project scope | 5 | One significant, distinct user story per member, with no overlap |
| API usage | 5 | Significant, well-motivated API use; slides show specifically how it is used (OpenStreetMap tiles, TTC data) |
| Data persistence | 5 | **Saving and loading both shown** in the demo (profile and saved trips) |
| Clean Architecture | 10 | No Dependency Rule violations; architecture diagrams as evidence; any minor violation discussed and justified |
| SOLID and design patterns | 10 | Rich discussion of **two or more** SOLID principles, an extensibility example, and **a pattern not taught in class** |
| Code quality | 10 | **Checkstyle is required for 4/5+** (evidence of use). For 5/5, show a representative PR with review |

Individual credit is also based on git logs, PRs, peer evaluations and TA observations. Everyone must commit their own code regularly.

## 2. Clean Architecture

**The Dependency Rule:** source code dependencies point **inward only**.

```
Frameworks & drivers   view (Swing), data_access (files, APIs)
Interface adapters     controller, presenter, view model
Use cases              interactor, input/output boundaries, input/output data, data access interfaces
Entities               Station, Connection, TransitNetwork, Route, AccessibilityProfile
```

- Entities import nothing from other layers.
- Use cases import only entities. Their interfaces (boundaries, data access interfaces) live **in the use case layer**.
- Interface adapters import use cases and entities, never Swing views or file code.
- `app/AppFactory` (main component) is the only place that knows every concrete class and wires them together.

**One use case end to end (the CA engine):**

1. **View → Controller.** The user clicks a button and the view calls the controller.
2. **Controller → InputBoundary.** The controller builds the `InputData` and calls the interactor through the `InputBoundary`.
3. **Interactor → DataAccessInterface.** The interactor reads what it needs (implemented in `data_access`).
4. **Interactor → Entities.** The interactor uses entities to do the work.
5. **Interactor → OutputBoundary.** The interactor builds the `OutputData` and calls the presenter through the `OutputBoundary`.
6. **Presenter → ViewModel.** The presenter updates the view model, which fires a property change.
7. **View redraws.** The view observes the view model and redraws itself.

The `InputBoundary` method returns `void`. Results flow out through the presenter, never back to the controller.

**Each use case needs:** an `XInputBoundary`, `XInputData`, `XInteractor`, `XOutputBoundary`, `XOutputData`, data access interfaces, `XController`, `XPresenter`, `XViewModel`/`XState`, a view, and unit tests for the interactor using fake data access and a fake presenter.

## 3. SOLID: where it shows up in our code

| Principle | Meaning | Example in AccessGraph |
| --- | --- | --- |
| **S**ingle responsibility | One reason to change per class | Presenter only formats text; `DijkstraRouteFinder` only searches |
| **O**pen/closed | Extend without modifying | New routing strategies implement `RouteFinder`; the interactor is unchanged |
| **L**iskov substitution | Subtypes work wherever the parent type is expected | Any `TransitNetworkDataAccessInterface` (file, test fake) works in the interactor |
| **I**nterface segregation | Small, focused interfaces | Separate `ProfileDataAccessInterface` and `TransitNetworkDataAccessInterface` |
| **D**ependency inversion | Depend on abstractions; high-level code owns the interface | The interactor depends on interfaces that `data_access` implements |

## 4. Design patterns

- **Taught in class:** Factory, Builder, Strategy, Observer, Facade, Adapter.
- **Already used:**
  - **Strategy:** `RouteFinder` / `DijkstraRouteFinder`.
  - **Observer:** `PlanRouteViewModel` with property change listeners.
  - **Decorator (not taught in class):** `DisruptionAwareNetworkDataAccess` wraps a network gateway and applies the active disruptions. `JourneyRecorder` wraps the plan-route input boundary and remembers the last search. Neither wrapped class was edited.
- **Candidate pattern not taught in class (needed for 5/5): Specification/Composite.** A `ConnectionRule` interface with rules like step-free-only, max transfers and disrupted-connection, combined with an `AllRules` composite. Disruptions (Story 3) would become another rule, which is a clean extensibility example.

## 5. Code quality checklist

- Every change goes through a **branch → PR → at least one teammate review → merge**. Never push to `main` directly.
- **Add Checkstyle** (`maven-checkstyle-plugin` with Google checks; see the course notes "Code Style and Documentation"). Screenshot the output for the slides.
- Javadoc on public classes and methods. Run `mvn test` before every push.
- Keep one strong example PR (clear description, review comments addressed) for the presentation.

## 6. Git workflow

```sh
git switch main && git pull
git switch -c feat/<short-name>      # one branch per issue
# edit, then:
mvn test
git add <files>
git commit -m "Describe the change"
git push -u origin feat/<short-name>
```

- Open a PR on GitHub and write `Closes #<issue>` in the description.
- Request a review, then merge after approval. Delete the branch afterwards.
- **If `main` moved while you worked:** `git switch main && git pull && git switch feat/x && git merge main`. Resolve conflicts in IntelliJ under **Git → Resolve Conflicts**.
- **Safe undo commands:**
  - `git restore <file>` discards uncommitted edits.
  - `git stash` / `git stash pop` temporarily sets aside changes and brings them back.
- **Never** `git push --force` on a shared branch.

## 7. User Story 1 (accessible route planning): status

**MVP done:**
- Lines 1, 2 and 4 are in `src/main/resources/data/ttc-connections.tsv`, with estimated 2-minute hops and a 5-minute transfer penalty.
- Dijkstra searches on (station, line, transfers).
- Step-free passengers can ride through any station, but can only board, change lines and exit at stations the TTC confirms as accessible.
- A transfer limit is enforced.
- Clear messages for the same station, unsupported stations, an inaccessible origin or destination, and no feasible route.

**Interfaces teammates build on:**
- **Story 2 (profile):** implement `ProfileDataAccessInterface` with saved data, replacing `InMemoryProfileDataAccess`, and extend `AccessibilityProfile`.
- **Story 3 (disruptions):** supply a `TransitNetwork` without the disrupted connections, or add a connection rule, then call the interactor again.
- **Story 4 (saved trips):** call `PlanRouteInputBoundary` with a saved origin and destination. Compare the `Route` and output data before and after a disruption.

**Later (learning and polish):**
- Rewrite and understand Dijkstra yourself.
- Add the Specification/Composite rules.
- Add more lines (5 and 6) and real times from GTFS.
- Add Checkstyle and fix the warnings.
- Draw a PlantUML class diagram and a sequence diagram for the slides.

## 8. User Story 3 (disruption-aware rerouting): status

**MVP done:**
- A disruption (service outage or planned closure) makes one connection unavailable: one line between two neighbouring stations, in both directions. Stations are never disabled as a whole.
- `TransitNetwork.without(disruptions)` gives the network that is left. The original network is not changed.
- The **Disruptions** tab simulates a disruption and restores it. After each change it explains why the previous route is no longer valid and shows the replacement, or reports that no alternative exists.
- The route planner reads the network through `DisruptionAwareNetworkDataAccess`, so every search avoids disrupted connections. The tab title counts the active disruptions.
- Simulated disruptions are kept in memory, and the tab says when only simulated conditions apply.
- **Live TTC alerts:** **Load live TTC alerts** reads the TTC GTFS-Realtime subway feed. A `NO_SERVICE` alert that is in effect closes the connections inside its stretch, and the journey is recalculated. Alerts that match no supported connection are listed as not applied. Live and simulated disruptions are labelled in the list, and live ones cannot be restored by hand.

**Interfaces teammates build on:**
- **Story 4 (saved trips):** run saved trips through the `JourneyRecorder` that wraps `PlanRouteInputBoundary`, and they are planned under the current disruptions and recalculated when a disruption changes. `RerouteOutputData` carries the outcome and the current route for a before/after comparison.
- **Another alert source:** implement `ServiceAlertDataAccessInterface`. `LiveAlertsInteractor` and the route planner stay unchanged.

**For the slides (API usage):** the feed is requested with `java.net.http.HttpClient` in `TtcAlertGateway`, read in its text format, and translated to our stations with `ttc-station-stops.tsv`. Adding it did not change `PlanRouteInteractor`: the planner avoids live closures because it already reads the network through the disruption-aware gateway. That is an Open/Closed example.

**Later:**
- Elevator outages, which need entrance and transfer connections in the graph.
- Loading alerts without blocking the window. The request has a 5 second timeout and runs on the Swing thread.
