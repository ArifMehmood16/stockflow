# Medium publishing notes

Status: article draft ready for author review; not submitted to or published on Medium.

- Title: **From One Database to a Distributed System: Bottlenecks, Caches and Shards**.
- Subtitle: An interactive walkthrough of how database scaling changes performance, consistency and failure recovery—and why every improvement needs a second question.
- Article source: [Read the draft](distributed-systems-simulator.md).
- Suggested topics: Distributed Systems, Databases, System Design, Software Engineering, Learning.
- Suggested cover: `docs/images/scaled-system.png`. Caption it as an educational simulation, not a benchmark.
- Inline screenshots, in order: `database-bottleneck.png`, `cache-added.png`, `scaled-system.png`, `cache-stampede.png`, `empty-shard.png`, `shard-ownership.png`. All are in `docs/images/`; the article supplies alt text and captions.

## Editorial approach

Use consistent British English, active voice and concrete explanations. Define jargon at first use, keep paragraphs focused, and use headings that advance the technical argument. Show engineering judgement through assumptions, counterexamples, invariants and failure conditions rather than claims about seniority. Do not invent employment experience, production incidents, benchmarks or durability guarantees.

Each experiment follows prediction → action → observation → trade-off. Distinguish behaviour demonstrated by this aggregate model from production considerations. Keep the simulation disclaimer before the first result, the distinction between request rates and storage work, and the limits on recovery claims. Blue-green deployment is reserved for a separate topic and is outside this article and simulator.

## Prepare the Medium version

1. Review the first-person account and AI-assistance disclosure before publication under your name.
2. Paste the article and set its title and subtitle separately. Upload the six screenshots at the marked positions; relative Markdown image links need uploads in Medium.
3. Preserve captions, alt text, the public demo link and primary-source citations. Check image legibility on desktop and mobile; readers can use the live demo to inspect individual cards.
4. Run the three short experiments from a reset: baseline → cache → API; stampede → mitigation; empty shard → ownership transfer. Protections persist between lessons, so reset when comparing untreated faults.
5. Check the published demo shows five chapters and no deployment card before sharing.
6. Publish only after the author's review. Add the resulting Medium URL to README.md and retain this Markdown source.

No Medium account or publication was accessed. Screenshots show the actual browser interface; displayed throughput is simulated. Historical documentation and log entries retain earlier scope as historical evidence.
