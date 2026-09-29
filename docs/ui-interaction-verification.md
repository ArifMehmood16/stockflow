# Prototype layout and interaction checks

Scope: owner-requested PLAN 0.4f–0.4g. These checks concern the illustrative browser model; they do not validate real distributed infrastructure or accept Cursor V0.

## Expected behavior

- Every component reserves separate space for its heading, technology, metadata and actions. Topology rows have visible gutters. The architecture initially fits; pan/zoom and Fit system are now explicitly allowed by the owner.
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
9. Drag blank canvas space, use the zoom buttons and scroll over the diagram. Confirm pointer-centered zoom, bounded panning, and Fit system. Focus the canvas and use arrows, +/− and 0. Keyboard focus must bring offscreen component controls into view. Repeat at 375px width without horizontal page overflow.
10. Build a component while zoomed and compare the transform before/after: it must not reset to Fit. Check hover placement after navigating. Inspect the replica's incoming paths in blue and green: REPLICA READ is green; ASYNC WAL is gold; PRIMARY WRITE remains purple and ends at the primary. Replica crash disables its two paths and restores primary reads.

Useful browser measurements: each `.node`, `.component-action` and `.shard-cell` should have `scrollHeight <= clientHeight + 1`; each `.component-shell` bounding rectangle should be within `.graph-scroll`; a visible tooltip rectangle must not intersect the inspected card. Tooltip `pointer-events` must be `none`.

The card-containment check applies in Fit mode. A deliberately zoomed/panned view may clip components; hover and focus must use the transformed positions.

## Observed verification, 2026-09-29

Before the fix, all nine card headers overflowed vertically, the workload action row extended outside its card, and tooltip placement was fixed to the canvas bottom.

After the fix, browser checks at 1280×720, Focus canvas and 375×812 found no card/control overflow or cards outside the canvas. Six-shard expansion and migration controls fit. Initial two-shard migration produced 500,000 records per shard. Before the cursor-policy refinement, a six-shard migration also conserved one million records (187,500 in four shards; 125,000 in two).

Pointer movement changed tooltip coordinates; the overlay ignored pointer events. Keyboard focus linked `aria-describedby`, Escape dismissed it, and the green tooltip changed from Provisioning to Ready without refocusing. Redis fault and mitigation explanations updated correctly. A new S2 tile used `shard-arrive` while S1 reported no animation; ownership transfer used `bucket-transfer`; all six tiles reported no animation with Motion off. The newly added S6 tooltip fit the narrow viewport and did not intersect its cluster card.

`make verify` passed 28 Java tooling assertions, the API suite, all 20 model/hover tests, JavaScript syntax checks and 202 local documentation links. Existing Maven/JDK deprecation/native-access warnings remain. The tests include cursor placement/edge fallback, dynamic lifecycle/fault summaries and new-shard ownership details. No dependency, database import or Docker execution was added. Phone maps still shrink labels to preserve the full topology; Inspector provides readable detail.

## Pan/zoom and replica-routing verification, 2026-09-29

PLAN 0.4g: moved components to a 1240×660 scene with 100px vertical gutters and 264px between API and primary. Route definitions now carry source, destination, flow type and label together. Writes target primary/shard owners; replica receives green reads and gold WAL. Route highlighting preserves these colors, patterns and matching arrowheads. Replica failure or lag protection restores primary reads.

The route-clearance test initially failed because a replica-read segment crossed the shard card; moved it into the right gutter and observed green. Browser checks confirmed blue/green destinations, semantic colors after highlighting, replica-crash fallback and hover after zoom. Green build/switch preserved `translate(291.167px, -23.75px) scale(0.541667)` exactly. Mouse drag changed the translation, wheel zoom increased 68% to 97%, and pointer release cleared the dragging state. Keyboard arrows/zoom/Fit and 375×812 Fit passed with no page horizontal overflow. An offscreen keyboard-focus check initially failed; after the focus-reveal fix, the replica card was fully visible and native scroll offsets remained zero.

Final full `make verify` passed 28 tooling assertions, the API suite, 26 model/hover/topology tests, syntax and 203 documentation links. Focused syntax/tests were repeated after the final focus/pointer cleanup. Historical 0.4f fit-only restrictions are superseded by the owner's explicit pan/zoom request. No backend, database or V0 verdict was changed.
