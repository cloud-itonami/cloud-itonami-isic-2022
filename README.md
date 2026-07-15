# cloud-itonami-isic-2022: Manufacture of paints, varnishes and similar coatings, printing ink and mastics

Open Business Blueprint for **ISIC Rev.5 2022**: manufacture of paints, varnishes and similar coatings, printing ink and mastics — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **plant operations**: production-batch data logging (product-type/weight/viscosity/VOC-content/pigment-dispersion-quality), mixing/dispersion-equipment (high-speed disperser, bead/ball mill) and tinting/filling-line maintenance scheduling, safety-concern flagging, and outbound shipment coordination.

This repository designs a forkable OSS business for paint/varnish/
coating/printing-ink/mastic plant operations: run by a qualified
operator so a plant keeps its own operating records instead of
renting a closed SaaS.

## Scope: one plant-operations shape, several related product families

ISIC 2022 covers a single manufacturing shape spanning several related
product families: **paints** (interior flat/non-flat, exterior),
**varnishes and lacquers** (varnish, lacquer, primer/sealer/
undercoat), **similar coatings** (industrial, marine), **printing
ink** (offset, flexographic, gravure), and **mastics, sealants and
caulks**. Every family shares the same pigment-dispersion (high-speed
disperser or bead/ball mill) + tinting/filling-line plant shape and
the same back-office coordination actor design (verified/registered
equipment+batch gate, permanent equipment-actuation block) — this
repo does not split them into separate actors. This is distinct from a
chemical-process primary-forms plant (e.g. `cloud-itonami-isic-2013`)
or a soap/detergent/cosmetics plant (e.g. `cloud-itonami-isic-2023`):
this plant's own hazard profile is chemical (solvent VOC exposure and
flammability during dispersion/mixing) AND regulatory (VOC-content
disclosure and ceiling compliance for essentially every product
category, modeled on the U.S. EPA Architectural & Industrial
Maintenance (AIM) VOC-content-limit framework and the EU Decopaint
Directive 2004/42/EC), not a saponification or polymerization-reactor
hazard.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — mixing/dispersion/tinting batch, viscosity/VOC-content, output-quality (pigment-dispersion/fineness-of-grind) data logging (administrative, not an operational decision)
- `:schedule-maintenance` — mixing/dispersion-equipment (high-speed disperser, bead/ball mill) or tinting/filling-line maintenance scheduling proposal
- `:flag-safety-concern` — surface a chemical-hazard (solvent VOC exposure)/flammability/equipment-safety concern (always escalates)
- `:coordinate-shipment` — outbound product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical, regulated domain**
(mixing/dispersion-equipment and tinting/filling-line equipment,
solvent-VOC/flammability chemical hazard, VOC-content regulatory
disclosure obligation):

- Does NOT control mixing/dispersion-equipment or tinting/filling-line equipment directly
- Does NOT make plant-safety or product-safety decisions (that's the plant supervisor's exclusive human authority)
- Does NOT actuate the mixing/dispersion or filling line (human plant supervisor decides)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human approval
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`paintmfg.operation/build`, a langgraph-clj StateGraph):
1. **`paintmfg.advisor`** (sealed intelligence node, `PaintAdvisor`): proposes decisions only, never commits
2. **`paintmfg.governor`** (independent, `Paint & Coatings Plant Operations Governor`): validates against domain rules, re-derived from `paintmfg.registry`'s pure functions and `paintmfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct mixing/dispersion-line-equipment control)
     - Directly actuating the mixing/dispersion or filling line (`:actuate-line? true`) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped weight past its own logged production weight (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:product-type` value on a production-batch patch
     - No physically implausible `:viscosity-cp` value on a production-batch patch
     - No physically implausible `:fineness-of-grind-hegman` (pigment-dispersion-quality) value on a production-batch patch
     - A batch's own declared `:voc-content-g-per-l` must independently stay within its product type's own regulatory ceiling — never taken on the advisor's self-report that the formulation "is compliant"
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`paintmfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`paintmfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
