# I Removed the Backend to Make My Distributed Systems Demo Better

*A browser-based simulator for seeing how caching, replication and sharding move bottlenecks—and introduce new problems.*

Adding another service instance is easy to draw on an architecture diagram. Explaining why it might do nothing for throughput is harder.

That is the question behind **Distributed Systems Simulator**, a portfolio project built around a fictional inventory system called StockFlow. You start with one API and one database, turn up the load, and add solutions directly to the architecture. The routes change, the numbers change, and the next limitation becomes visible.

[Try the live simulator](https://arifmehmood16.github.io/stockflow/) · [Explore the source](https://github.com/ArifMehmood16/stockflow)

The tool runs entirely in your browser. It does not start Java services, connect to PostgreSQL or generate HTTP load. That boundary is a deliberate design choice, not a missing setup step.

## The detour that clarified the product

The project initially included a real Java API, a local PostgreSQL dataset and a request generator. That made it possible to investigate real behavior, but it also changed what the demo was about.

Instead of focusing on the architecture, I was dealing with run configuration, connection limits, startup, timeouts and the difference between a request and a multi-request workload cycle. An HTTP connection timeout was a valid observation—but it did not, by itself, demonstrate the database bottleneck I wanted someone to understand.

There was also a more basic mismatch: someone evaluating a portfolio should be able to open the project and explore it. They should not need to import a dataset and configure a database before reaching the interesting part.

I narrowed the product to an educational simulator. The earlier backend experiment remains in Git history. The current application has one job: make a design trade-off understandable through interaction.

## A bottleneck you can reproduce in two minutes

The initial scenario uses explicit assumptions:

- One API can handle 22,000 requests per second.
- The database can handle 12,000 operations per second.
- Traffic is 90% reads and 10% writes.
- A healthy cache serves 80% of eligible reads.

These values are teaching constants. They are not benchmarks for Java, Redis or PostgreSQL, and they are not production-sizing advice.

Set the offered load to **30,000 requests per second**. In the baseline model, all of that work reaches the database. Its assumed capacity is the first limit, so about **12,000 requests per second** complete.

![The baseline model at 30,000 offered requests per second, with 12,000 completing.](../images/database-bottleneck.png)

*The displayed values are calculated by the simulation. No requests are sent to a real service.*

Now build Redis on the map. With 27,000 reads and 3,000 writes per second, an 80% read-hit assumption leaves:

**27,000 × 20% + 3,000 = 8,400 database operations per second.**

Database demand now fits within the assumed 12,000-operation capacity. But the application still cannot complete all 30,000 requests: the single API becomes the next limit at **22,000 requests per second**.

![Adding the simulated cache lowers database demand and exposes the API limit.](../images/cache-added.png)

Add a second API instance. Its readiness animation finishes, requests are routed across the instances, and the modeled system can now complete **30,000 requests per second**.

![With caching and another API, the model handles the selected load.](../images/scaled-system.png)

The point is the sequence. Adding an API before reducing database demand leaves the shared database as the bottleneck. After caching, that same API addition becomes useful. The value of a change depends on the constraint it addresses.

## Every solution should introduce a question

A cache cannot be presented as a permanent “make it faster” button. In the simulator, you can inject a stampede, stale data, penetration, a hot key or a cache crash. The explanation changes with the fault and its mitigation.

The same idea carries through the other lessons:

**Read replicas:** eligible reads can move away from the primary, but writes still need it. Lag introduces a consistency trade-off. The lesson distinguishes read capacity from primary write capacity.

**Sharding:** a new shard starts empty. It does not contribute balanced capacity until ownership moves. The model represents one million virtual records through 16 ownership buckets, so you can see why adding a node and moving data are separate steps. Skew and interrupted migration remain visible problems.

**Failure recovery:** a failed primary must be fenced before promotion. The simulator illustrates this ordering; it does not claim to reproduce real data recovery or prove durability.

**Blue/green deployment:** the new version must become ready before traffic switches. A broken version needs a rollback path. Drawing a green box is only the beginning of the lesson.

These experiments share one evolving architecture. Moving between chapters preserves the current components, load and installed protections. That makes it possible to compare changes within the same system.

## A small implementation with an explicit model

The application uses HTML, CSS and JavaScript modules without a frontend framework or runtime package dependencies.

The implementation separates four concerns:

- State transitions and capacity formulas.
- Rendering, lesson guidance and staged animations.
- Component geometry, request routes and pan/zoom.
- Contextual explanations and hover placement.

Most of the useful tests are small model tests: an unfinished component must not receive traffic; a replica must not increase primary write capacity; adding an empty shard must not instantly rebalance ownership; offered requests must be accounted for as completed or rejected.

A regression also checks the walkthrough above: the progression from 12,000 to 22,000 to 30,000 modeled completions. The test already passing before a change is evidence of existing behavior, not a reason to invent new infrastructure.

I used AI coding assistance during development and kept a development log of changes, checks and scope decisions. The model, its limitations and the resulting user experience still require review; generated code is not evidence that a distributed-systems claim is correct.

## What this model deliberately leaves out

This is an aggregate model, not a network emulator. It does not reproduce queueing distributions, consensus, transaction logs, physical replication, CPU contention or real latency percentiles.

The request particles are sampled animation. They are not one particle per request. The million-record label represents virtual ownership counts, not an allocated dataset. Build timers provide visual pacing rather than provisioning measurements.

Those omissions make the explanation manageable, but they also limit the conclusions someone can draw. A scenario can show why a bottleneck moves under stated assumptions. It cannot tell you how many instances your production system needs.

I chose to make those boundaries visible in the interface and README. A convincing animation should not turn an assumption into an apparent measurement.

## Making the portfolio easy to try

The public demo is hosted on GitHub Pages using its free public-repository offering. There is no paid server, database or custom domain. Publishing copies only the static application to a dedicated deployment branch; local configuration and legacy data are not part of the site.

Locally, `make run` serves the same files. `make stop` stops that preview. Docker is optional and serves the same static application in a single container.

Removing the backend made the project less ambitious in infrastructure and more focused as a teaching tool. A visitor can now change one component, see the effect and ask whether the next problem has actually been addressed.

[Open the simulator](https://arifmehmood16.github.io/stockflow/), set the load to 30,000 requests per second, and try adding another API before adding the cache. Then reset and reverse the order. The comparison is the lesson.

---

Source and implementation notes: [repository](https://github.com/ArifMehmood16/stockflow), [simulation model](https://github.com/ArifMehmood16/stockflow/blob/main/docs/SIMULATION.md), [GitHub Pages availability](https://docs.github.com/en/pages/getting-started-with-github-pages).
