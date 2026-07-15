# ADR-0001: PaintAdvisor ⊣ Paint & Coatings Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2022` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-2022` publishes an OSS blueprint for paints,
varnishes and similar coatings, printing ink, and mastics **plant
operations coordination** (production-batch product-type/weight/
viscosity/VOC-content/pigment-dispersion-quality data logging,
mixing/dispersion-equipment and tinting/filling-line maintenance
scheduling, safety-concern flagging, and outbound shipment
coordination). Like every actor in this fleet, the blueprint alone is
not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

The closest domain analog is `cloud-itonami-isic-2023` (Manufacture of
soap and detergents, cleaning and polishing preparations, perfumes and
toilet preparations): both are back-office coordination actors for a
fixed processing PLANT with heavy manufacturing equipment and a real
physical safety dimension, and both share the same four-op shape
(`:log-production-batch`/`:schedule-maintenance`/`:flag-safety-
concern`/`:coordinate-shipment`) and the same two-entity verified/
registered gate structure (equipment for maintenance scheduling, batch
for shipment coordination). The two verticals are, however, distinct
plants with distinct hazard AND regulatory profiles: 2023's hazard is
caustic-alkali/surfactant handling during saponification/mixing plus a
fragrance-allergen labeling obligation that applies only to a subset
of its product types, while 2022's hazard is solvent-VOC exposure and
flammability during pigment dispersion/mixing AND a VOC-content
regulatory ceiling obligation that applies to EVERY product type in
its combined scope (modeled on the U.S. EPA Architectural & Industrial
Maintenance (AIM) VOC-content-limit framework and the EU Decopaint
Directive 2004/42/EC). This build mirrors 2023's architecture closely
but adapts the hazard profile, equipment/product vocabulary, and adds
two genuinely new domain-specific governor checks (viscosity and
fineness-of-grind/pigment-dispersion-quality plausibility validation)
plus one regulatory-ceiling check (VOC-content) in place of 2023's
conditional fragrance-allergen-labeling-completeness check: 2022's
permanent equipment-actuation block guards a mixing/dispersion or
tinting/filling LINE (`:actuate-line?`) rather than a formulation/
filling LINE; 2022's production-batch record declares a
`:product-type` (spanning paints, varnishes/lacquers/undercoats,
similar coatings, printing ink, and mastics/sealants/caulks, per ISIC
2022's own combined scope), a `:viscosity-cp`, a
`:fineness-of-grind-hegman` (pigment-dispersion quality on the
ASTM D1210 Hegman/grind-gauge scale), and a `:voc-content-g-per-l`
that must independently stay within its product type's own closed
regulatory ceiling before the batch patch may commit.

This vertical has NO pre-existing `kotoba-lang/paintmfg`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic — pure functions in
`paintmfg.registry` (equipment/batch verification, shipment-weight
recompute, product-type validation, viscosity plausibility
validation, fineness-of-grind plausibility validation, VOC-content
regulatory-ceiling validation) are re-verified independently by the
governor, the same "ground truth, not self-report" discipline
established across prior actors (most directly
`cloud-itonami-isic-2023`'s `soapmfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:paint-coatings-plant-operations-governor`, is grep-verified UNIQUE
fleet-wide (`gh search code "paint-coatings-plant-operations-governor"
--owner cloud-itonami`, zero hits before this repo was created); so is
the `paintmfg` namespace prefix (`gh search code "paintmfg" --owner
cloud-itonami`, zero hits).

## Decision

### Decision 1: Self-contained domain logic (no external paint/coatings/ink/mastic-manufacturing capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
paint/varnish/coating/printing-ink/mastic vertical has NO pre-existing
capability library to wrap. The equipment/batch-verification /
shipment-weight / product-type / viscosity / fineness-of-grind / VOC-
content validation functions live as pure functions in
`paintmfg.registry` and are re-verified independently by
`paintmfg.governor` — the same "ground truth, not self-report"
discipline established across prior actors (most directly
`cloud-itonami-isic-2023`'s `soapmfg.registry`).

### Decision 2: Coordination, not control — scope boundary at the back-office

This actor is **strictly back-office coordination** of paint/varnish/
coating/printing-ink/mastic plant operations. It does NOT:
- Control mixing/dispersion-equipment (high-speed disperser, bead/ball mill) or tinting/filling-line equipment directly
- Make plant-safety or product-safety decisions (exclusive to the human plant supervisor)
- Actuate the mixing/dispersion or filling line

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority —
it is a proposal-screening and documentation layer.

**CRITICAL SAFETY BOUNDARY**: paint/varnish/coating/printing-ink/
mastic manufacturing is a safety-critical and regulated domain
(solvent-VOC/flammability chemical hazard, VOC-content regulatory
disclosure obligation, heavy material handling). Safety-concern
flagging NEVER auto-commits. All safety concerns escalate immediately
to human review.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (chemical-hazard solvent-VOC-exposure concern,
flammability concern, equipment-safety concern, crew fatigue) ALWAYS
escalates, never auto-commits. This is not a "low-stakes proposal" —
it is a circuit-breaker that must reach human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-2023`, this vertical has TWO entity kinds
each gating a different op: `:schedule-maintenance` independently
verifies the referenced **equipment** unit's own `:verified?`/
`:registered?` fields; `:coordinate-shipment` independently verifies
the referenced **batch**'s own `:verified?`/`:registered?` fields.
Both are the same "plant/batch record must be independently
verified/registered before any action" HARD invariant applied to the
two distinct record kinds this domain actually has.
`:coordinate-shipment` additionally independently recomputes whether a
batch's own recorded shipped-to-date weight plus the proposal's own
claimed weight would exceed the batch's own recorded production
weight — never taken on the advisor's self-report.

### Decision 5: Viscosity, fineness-of-grind, and VOC-content-ceiling — three new independently-verified checks

Unlike `cloud-itonami-isic-2013` (no analogous regulatory disclosure
obligation) and unlike `cloud-itonami-isic-2023` (a CONDITIONAL
fragrance-allergen-labeling-completeness check limited to fragrance-
bearing product types), this vertical adds THREE new governor checks,
one plausibility pair and one universal regulatory-ceiling check:
- `:log-production-batch` INDEPENDENTLY re-validates a patch's own
  declared `:viscosity-cp` against a physically plausible range
  (`paintmfg.registry/viscosity-valid?`) — a fabricated or sensor-error
  reading is rejected rather than let through.
- `:log-production-batch` INDEPENDENTLY re-validates a patch's own
  declared `:fineness-of-grind-hegman` (pigment-dispersion quality,
  ASTM D1210 Hegman/grind-gauge scale) against the gauge's own
  physical range (`paintmfg.registry/fineness-of-grind-valid?`).
- `:log-production-batch` INDEPENDENTLY re-derives the effective
  product type (patch's own `:product-type`, else the batch's
  already-recorded type) and, when the patch declares a
  `:voc-content-g-per-l`, checks it against that product type's own
  closed regulatory ceiling (`paintmfg.registry/voc-content-exceeds-
  limit?`, modeled on the U.S. EPA AIM VOC-content-limit framework /
  EU Decopaint Directive 2004/42/EC) — UNLIKE 2023's fragrance-
  allergen check (which requires a completeness FLAG to be true),
  this check requires the declared VALUE itself to independently stay
  under a closed numeric ceiling, applicable to every product type in
  this vertical's scope rather than a conditional subset. This mirrors
  the "ground truth, not self-report" discipline every other governor
  check in this fleet establishes, applied to genuinely new
  domain-specific quality and regulatory facts this vertical's own
  product mix introduces.

### Decision 6: HARD invariants (no override)

Four HARD governor invariants (elaborated into twelve concrete checks
in `paintmfg.governor`, mirroring `cloud-itonami-isic-2023`'s own
elaboration of its HARD invariants into concrete checks, plus the
three new domain-specific checks per Decision 5) block proposals and
cannot be overridden by human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's weight must independently recompute within the batch's own logged production weight
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct mixing/dispersion-line-equipment control or line actuation is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Paint/varnish/coating/printing-ink/mastic plant operations
back-office now has a documented, governed, auditable coordination
layer that funnels all decisions through independent validation before
human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human plant-
supervisor sign-off.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into twelve concrete governor checks) protect against scope creep into
unauthorized equipment operation, line actuation, or non-compliant VOC
content. Safety concerns are a circuit-breaker, not a threshold.

(+) Safety-critical and regulatory discipline is explicit:
safety-concern flagging cannot be rate-limited, suppressed, or
auto-decided by phase gate; VOC-content compliance is independently
re-verified against a closed per-product-type ceiling, never taken on
trust. Human review is mandatory for the former.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation and mixing/dispersion/filling-line
operation remain human-controlled via external channels.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch, or an authoritative
multi-jurisdiction VOC-content regulatory database) — this is a
standalone coordinator blueprint; the closed `voc-limit-g-per-l` table
is a representative, illustrative subset of the EPA AIM / EU Decopaint
frameworks, not an exhaustive multi-jurisdiction regulatory database.

## Verification

- `cloud-itonami-isic-2022`: `clojure -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `clojure -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-weight-exceeded, line-actuate-blocked,
  already-scheduled, invalid-product-type, invalid-viscosity,
  invalid-fineness-of-grind, voc-content-exceeds-limit).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `clojure -M:test` resolves offline inside the monorepo checkout.
