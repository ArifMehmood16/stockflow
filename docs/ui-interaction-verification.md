# Prototype layout and interaction checks

Scope: owner-requested PLAN 0.4f. These checks concern the illustrative browser model; they do not validate real distributed infrastructure or accept Cursor V0.

## Expected behavior

- Every component reserves separate space for its heading, technology, metadata and actions. Topology rows have visible gutters; the whole architecture remains fitted into one view.
- Hover follows the pointer and flips/clamps at viewport edges. It may overlap surrounding content, as requested by the owner, but leaves the inspected card clear. It never intercepts pointer input. Keyboard focus uses a component anchor; Escape, blur, scrolling or an action dismisses it.
- Hover and the click-to-pin Inspector reflect current build/readiness, fault, mitigation and shard ownership state. Values remain explicitly illustrative.
- Construction animates inside a fixed component slot. A new shard tile animates once; existing tiles retain their DOM identity and do not replay arrival on unrelated renders. Changed ownership flashes the affected tiles. Motion off disables these effects.
- New shards are inspectable immediately. An empty shard has no owned records until the migration sequence finishes.

## Repeatable browser smoke

Use `make preview` (or an unused `PORT`) and refresh after restarting the Java preview when static routes change.

1. At 1280×720, check baseline cards and Focus canvas. Card metadata must stay above the action row. Keep the learning path and Inspector usable.
2. Move across Redis's header: the tooltip follows, leaves Redis itself clear, and remains readable. Move into Build Redis and click; the tooltip dismisses and the action works.
3. Focus a building component with the keyboard. Observe Provisioning → Checking readiness → Ready in the same tooltip without refocusing. Escape dismisses it; click opens Inspector.
4. Build Redis, select Stale cache, Inject and apply Version guard. Hover and Inspector must show the fault and mitigation explanations.
5. Add a second shard. S1 must not replay its arrival animation; S2 should arrive empty. Inspect S2, then Pause writes → Copy buckets → Verify copy → Switch owners. Its Inspector must update to 500,000 logical records; ownership changes flash both tiles.
6. Expand to six shards. Exercise all three migration controls, including Cancel. Tiles, counts and controls must stay inside the cluster card. Turn Motion off: arrivals and ownership flashes must stop.
7. At 375×812, verify all component cards fit the canvas and the page has no horizontal overflow. Hover a newly added shard; its tooltip must fit the viewport and clear the cluster card. Tap/click for readable Inspector details.
8. Build green, cache and replica; switch at the router. Paths must go through the gutters around other cards. Test rollback and reset; no stale tooltip or removed shard should remain selected visually.

Useful browser measurements: each `.node`, `.component-action` and `.shard-cell` should have `scrollHeight <= clientHeight + 1`; each `.component-shell` bounding rectangle should be within `.graph-scroll`; a visible tooltip rectangle must not intersect the inspected card. Tooltip `pointer-events` must be `none`.

## Observed verification, 2026-09-29

Before the fix, all nine card headers overflowed vertically, the workload action row extended outside its card, and tooltip placement was fixed to the canvas bottom.

After the fix, browser checks at 1280×720, Focus canvas and 375×812 found no card/control overflow or cards outside the canvas. Six-shard expansion and migration controls fit. Initial two-shard migration produced 500,000 records per shard. Before the cursor-policy refinement, a six-shard migration also conserved one million records (187,500 in four shards; 125,000 in two).

Pointer movement changed tooltip coordinates; the overlay ignored pointer events. Keyboard focus linked `aria-describedby`, Escape dismissed it, and the green tooltip changed from Provisioning to Ready without refocusing. Redis fault and mitigation explanations updated correctly. A new S2 tile used `shard-arrive` while S1 reported no animation; ownership transfer used `bucket-transfer`; all six tiles reported no animation with Motion off. The newly added S6 tooltip fit the narrow viewport and did not intersect its cluster card.

`make verify` passed 28 Java tooling assertions, the API suite, all 20 model/hover tests, JavaScript syntax checks and 202 local documentation links. Existing Maven/JDK deprecation/native-access warnings remain. The tests include cursor placement/edge fallback, dynamic lifecycle/fault summaries and new-shard ownership details. No dependency, database import or Docker execution was added. Phone maps still shrink labels to preserve the full topology; Inspector provides readable detail.
