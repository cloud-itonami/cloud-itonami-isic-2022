(ns paintmfg.registry
  "Pure-function domain logic for the paints, varnishes and similar
  coatings, printing ink and mastics plant-operations coordination
  actor -- equipment/batch verification, shipment-weight recompute,
  product-type validation, viscosity plausibility validation,
  pigment-dispersion (fineness-of-grind) plausibility validation, VOC
  (volatile organic compound) content regulatory-ceiling validation,
  and draft maintenance-schedule/shipment-coordination record
  construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/paintmfg`-style capability library to wrap
  (verified: no such repo exists). The domain logic therefore lives
  here as pure functions, re-verified INDEPENDENTLY by
  `paintmfg.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (e.g.
  `soapmfg.registry/shipment-weight-exceeded?` from
  `cloud-itonami-isic-2023`, this actor's closest chemical-process-
  plant analog): never trust a proposal's own self-reported weight/
  status/VOC-content when the inputs needed to recompute it
  independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant-operations system. It builds the DRAFT record
  a plant coordinator would keep (a scheduled maintenance window, a
  coordinated shipment), not the act of actuating a mixing/dispersion
  line or dispatching a real freight carrier (this actor NEVER does
  either -- see README `What this actor does NOT do`).

  SCOPE note: ISIC 2022 covers paints, varnishes and similar coatings
  (interior/exterior architectural paints, varnishes, lacquers,
  primers/sealers/undercoats, industrial and marine coatings),
  printing ink (offset/flexographic/gravure), and mastics/sealants/
  caulks -- one plant-operations shape (pigment dispersion via
  high-speed disperser or bead/ball mill, letdown/tinting, filling)
  spanning several product families, the same combined-scope pattern
  `cloud-itonami-isic-2023`'s own soap/detergent/cleaning-preparation/
  perfume/toilet-preparation scope establishes. Every batch's own
  volatile-organic-compound (VOC) content is subject to a real
  regulatory ceiling (modeled on the U.S. EPA Architectural &
  Industrial Maintenance (AIM) VOC-content-limit framework and the EU
  Decopaint Directive 2004/42/EC) that varies by product category --
  `voc-content-exceeds-limit?` independently re-verifies a batch's own
  declared VOC content against that closed ceiling table, never taken
  on the advisor's self-report, the same discipline
  `soapmfg.registry/fragrance-allergen-labeling-incomplete?` applies
  to its own regulatory disclosure obligation.")

;; ----------------------------- constants -----------------------------

(def valid-product-types
  "The closed set of product-type values a production-batch record may
  declare -- spanning ISIC 2022's own combined scope: paints,
  varnishes and similar coatings, printing ink, and mastics/sealants/
  caulks. Anything else is a fabricated/unrecognized product type --
  the governor HARD-holds rather than let an invented product type
  pass through."
  #{;; paints
    :interior-flat-paint :interior-non-flat-paint :exterior-paint
    ;; varnishes/lacquers/undercoats
    :varnish :lacquer :primer-sealer-undercoat
    ;; similar coatings
    :industrial-coating :marine-coating
    ;; printing ink
    :offset-printing-ink :flexographic-printing-ink :gravure-printing-ink
    ;; mastics/sealants/caulks
    :mastic :sealant :caulk})

(def voc-limit-g-per-l
  "The closed VOC-content regulatory ceiling table (grams of VOC per
  litre of product, ready-to-use), one entry per `valid-product-types`
  member -- modeled on the U.S. EPA Architectural & Industrial
  Maintenance (AIM) VOC-content-limit framework and the EU Decopaint
  Directive 2004/42/EC category ceilings. Illustrative, representative
  values (not an exhaustive multi-jurisdiction regulatory database --
  see README `What this actor does NOT do`), the same 'representative
  subset, not exhaustive' scope `soapmfg.registry/
  known-fragrance-allergens` establishes for its own regulatory set."
  {:interior-flat-paint      50.0
   :interior-non-flat-paint  150.0
   :exterior-paint           250.0
   :varnish                  350.0
   :lacquer                  550.0
   :primer-sealer-undercoat  200.0
   :industrial-coating       450.0
   :marine-coating           450.0
   :offset-printing-ink      400.0
   :flexographic-printing-ink 400.0
   :gravure-printing-ink     400.0
   :mastic                   250.0
   :sealant                  250.0
   :caulk                    250.0})

(def viscosity-min-cp
  "Physical floor for a batch's own viscosity reading (centipoise, cP)
  -- a real coating/ink/mastic is never a zero-viscosity fluid."
  0.0)

(def viscosity-max-cp
  "Physical ceiling for a batch's own viscosity reading (centipoise,
  cP) -- generous enough to cover thin printing inks through heavily
  filled mastics/sealants/caulks, but a reading beyond this is
  implausible sensor/QC data, not a real batch."
  1000000.0)

(def fineness-of-grind-min-hegman
  "Physical floor of the Hegman/grind gauge scale (ASTM D1210) used to
  measure pigment-dispersion quality -- 0 is the coarsest reading the
  gauge can register."
  0.0)

(def fineness-of-grind-max-hegman
  "Physical ceiling of the Hegman/grind gauge scale (ASTM D1210) -- 8
  is the finest (best-dispersed) reading the gauge can register. A
  reading beyond this range is implausible sensor/QC data, not a real
  batch."
  8.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its product-type/weight/viscosity/VOC-content claims have
  actually been QC-inspected, not merely logged from an unverified
  intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-weight-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-to-date-kg` + `new-weight-kg` exceed `batch`'s own
  recorded `:weight-kg` (the batch's own logged production weight)?
  Needs no proposal inspection or stored-verdict lookup -- its inputs
  are permanent fields already on the batch's own record, the same
  shape every sibling actor's own cost/total-matching check uses."
  [batch new-weight-kg]
  (let [capacity (:weight-kg batch)
        so-far (:shipped-weight-kg batch 0.0)]
    (and (number? capacity)
         (number? new-weight-kg)
         (> (+ (double so-far) (double new-weight-kg)) (double capacity)))))

(defn product-type-valid?
  "Is `product-type` one of the closed, known product-type values
  (paint, varnish/lacquer/undercoat, similar coating, printing ink, or
  mastic/sealant/caulk)? nil/blank is treated as invalid (a
  production-batch patch must declare a real product type, not omit
  it silently)."
  [product-type]
  (contains? valid-product-types product-type))

(defn viscosity-valid?
  "Is `viscosity-cp` a physically plausible batch viscosity reading
  (centipoise)? Rejects nil, non-numbers, negative values, and values
  beyond `viscosity-max-cp` -- a fabricated or sensor-error reading,
  never let through as a real batch fact."
  [viscosity-cp]
  (and (number? viscosity-cp)
       (>= (double viscosity-cp) viscosity-min-cp)
       (<= (double viscosity-cp) viscosity-max-cp)))

(defn fineness-of-grind-valid?
  "Is `hegman` a physically plausible pigment-dispersion-quality
  reading on the Hegman/grind gauge scale (ASTM D1210, 0=coarsest,
  8=finest)? Rejects nil, non-numbers, and values outside the gauge's
  own physical range -- a fabricated or sensor-error reading, never
  let through as a real batch fact."
  [hegman]
  (and (number? hegman)
       (>= (double hegman) fineness-of-grind-min-hegman)
       (<= (double hegman) fineness-of-grind-max-hegman)))

;; ----------------------------- VOC-content regulatory checks -----------------------------

(defn voc-limit-for [product-type]
  (get voc-limit-g-per-l product-type))

(defn voc-content-exceeds-limit?
  "Ground-truth check for a `:log-production-batch` proposal:
  INDEPENDENTLY re-derive the EFFECTIVE product type (patch's own
  `:product-type`, else the batch's already-recorded type) and check
  whether the patch's own declared `:voc-content-g-per-l` exceeds that
  product type's own closed regulatory ceiling
  (`voc-limit-g-per-l`) -- never taken on the advisor's self-report
  that the formulation 'is compliant'. Modeled on the U.S. EPA AIM
  VOC-content-limit framework / EU Decopaint Directive 2004/42/EC.
  `effective-product-type` with no known ceiling (not in
  `valid-product-types`) is NOT independently flagged here --
  `invalid-product-type-violations` in `paintmfg.governor` already
  rejects a fabricated product type on its own."
  [effective-product-type voc-content-g-per-l]
  (boolean
   (and (some? voc-content-g-per-l)
        (let [limit (voc-limit-for effective-product-type)]
          (and (some? limit) (> (double voc-content-g-per-l) (double limit)))))))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  mixing/dispersion (high-speed disperser, bead/ball mill), tinting,
  or filling-line maintenance window against a verified, registered
  piece of equipment. Pure function -- does not actuate the
  dispersion/mixing/filling line or execute any maintenance; it builds
  the RECORD a plant coordinator would keep. `paintmfg.governor`
  independently re-verifies the equipment's own verified/registered
  ground truth, and permanently blocks any attempt to directly actuate
  the mixing/dispersion line (see README `Actuation`), before this is
  ever allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound paint/varnish/coating/printing-ink/mastic shipment against a
  verified, registered production batch. Pure function -- does not
  dispatch any real freight carrier; it builds the RECORD a plant
  coordinator would keep. `paintmfg.governor` independently
  re-verifies the shipment's own claimed weight against
  `shipment-weight-exceeded?`, before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
