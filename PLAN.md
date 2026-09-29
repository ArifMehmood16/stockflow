# Distributed Systems Simulator — current plan

The product is a standalone browser simulation. [README](README.md) defines the experience; [simulation design](docs/SIMULATION.md) defines the model. The former backend implementation plan is preserved in [the archive](docs/archive/README.md) and is not pending work.

## Delivered

- [x] One cumulative architecture with five guided learning chapters.
- [x] On-map builds, readiness, rerouting, fault injection and mitigation.
- [x] Caching, replicas, recovery, multiple shards lessons.
- [x] Explicit capacity assumptions and bottleneck explanations.
- [x] Browser-only runtime with no service, database or dataset dependency.
- [x] Minimal Make startup/stop and optional static-site Docker configuration.
- [x] Short model, routing, hover and static-host tests.
- [x] Recruiter-oriented name, README and GitHub topics.

- [x] Free GitHub Pages demo and current screenshots in the README.
- [x] Medium article draft with walkthrough, limitations and publishing notes.

## Future work, only when requested

- Publish the reviewed article to Medium when requested.
- Improve specific lessons or interactions based on feedback.

Do not rebuild backend services, add real load generation, introduce observability infrastructure, or run long stress tests. Use one focused regression before changing behavior, then `make verify` and a short browser check where relevant. Keep normal commits and merge completed work into `main` when authorized.
