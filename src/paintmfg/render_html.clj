(ns paintmfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  WHAT IS REAL HERE (and how it was verified):

  Every row on the generated page is produced by EXECUTING this repo's
  own actor stack -- `paintmfg.store/mem-store` + `sample-data!` ->
  `paintmfg.operation/build` -> `langgraph.graph/run*` (same thread-id /
  `:resume? true` approval convention `paintmfg.sim` uses) ->
  `paintmfg.governor` -> `paintmfg.phase`. Nothing on the page is
  hand-typed domain data:

    * the batch / equipment / safety-concern / draft-record tables are
      read back OUT of the store AFTER the run, so they show the ground
      truth the run actually left behind (e.g. batch-001's
      `:shipped-weight-kg` is 1000.0 + 500.0 because the ship-1 shipment
      really committed, and disperser-001's
      `:last-scheduled-maintenance-date` exists only because mnt-1
      really committed);
    * the scenario and audit-ledger tables render the real `:verdict` /
      `:disposition` / `:basis` values the graph produced -- the HOLD
      rule names are `paintmfg.governor`'s own `:rule` keywords, not
      prose;
    * the action-gate table is derived from LIVE values --
      `paintmfg.governor/allowed-ops`, `allowed-proposal-effects`,
      `high-stakes`, `confidence-floor`, `paintmfg.phase/phases` +
      `write-ops` + `default-phase`, and the `:stake` each op's real
      proposal actually declared during this run;
    * the VOC regulatory-ceiling table is
      `paintmfg.registry/voc-limit-g-per-l` itself.

  INPUT PROVENANCE: every entity id referenced by a request already
  exists in `paintmfg.store/sample-data!` -- batches `batch-001`,
  `batch-002`, `batch-003`; equipment `disperser-001`, `mill-002`.
  (`mnt-1`..`mnt-3`, `concern-1`, `ship-1`..`ship-3` are the ids of the
  NEW draft records this run creates, exactly as `paintmfg.sim` does --
  they are subjects being created, not references to entities that must
  pre-exist.) No field is rendered that is absent from the domain model.

  VERIFIED BEFORE WRITING THIS FILE: `clojure -M:dev:run` was executed
  and its output read -- the seeded ids, the four ops, and every HARD
  hold reproduced here came from that run, not from guesswork.

  DETERMINISTIC: no timestamp, no randomness, no wall-clock content;
  every set is sorted before rendering and the store's own accessors
  (`all-batches` / `all-equipment`) already sort by `:id`. Two
  consecutive runs are byte-identical (verify with `diff`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [paintmfg.governor :as governor]
            [paintmfg.operation :as op]
            [paintmfg.phase :as phase]
            [paintmfg.registry :as registry]
            [paintmfg.store :as store]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mirrored from
  `paintmfg.sim` (not calling its `-main`, to keep this namespace
  self-contained and free of println noise). Every referenced batch /
  equipment id below is a real `paintmfg.store/sample-data!` seed id.

  Clean lifecycle (governor clears -> phase gate -> commit):

    1. `:log-production-batch` batch-001, clean patch -- the ONLY
       auto-eligible op at phase 3, so it commits with no human.
    2. `:schedule-maintenance` mnt-1 on disperser-001 (verified +
       registered high-speed disperser) -- never auto at ANY phase ->
       escalate -> human approves -> commit.
    3. `:flag-safety-concern` concern-1 on disperser-001 -- ALWAYS
       high-stakes (`:coordination/safety-concern`) -> escalate ->
       approve -> commit.
    4. `:coordinate-shipment` ship-1 on batch-001, 500.0 kg (5000 kg
       logged, 1000 kg already shipped, so it fits) -> escalate ->
       approve -> commit.

  HARD holds -- none of these ever reaches a human:

    5. request `:effect` not `:propose`      -> `:not-propose-effect`
    6. unrecognized op                        -> `:unknown-op` +
                                                 `:line-control-blocked`
    7. maintenance on mill-002 (UNVERIFIED)   -> `:equipment-not-verified`
    8. shipment on batch-003 (UNVERIFIED)     -> `:batch-not-verified`
    9. shipment ship-3 on batch-002, 1000 kg
       (7500 shipped + 1000 > 8000 logged)    -> `:shipment-weight-exceeded`
   10. maintenance with `:actuate-line? true` -> `:line-actuate-blocked`
                                                 (PERMANENT: no phase and
                                                 no human approval can
                                                 ever override it)
   11. mnt-1 scheduled a second time          -> `:already-scheduled`
   12. fabricated `:product-type`             -> `:invalid-product-type`
   13. negative `:viscosity-cp`               -> `:invalid-viscosity`
   14. 12.0 Hegman (gauge maxes at 8.0)       -> `:invalid-fineness-of-grind`
   15. 400.0 g/L VOC on an interior-flat-paint
       batch (ceiling 50.0 g/L)               -> `:voc-content-exceeds-limit`

  Returns `{:db store :runs [{:label .. :tid .. :request .. :state ..}]}`
  -- `:state` is the graph's own final state, so the rendered
  disposition/verdict/proposal values are the actor's, not a copy."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)
        runs (atom [])
        step! (fn [label tid request & [approve?]]
                (let [r (exec! actor tid request)
                      r (if approve? (approve! actor tid) r)]
                  (swap! runs conj {:label label :tid tid :request request
                                    :state (:state r)})
                  r))]

    (step! "clean batch intake (auto-commit)" "t1"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:product-type :interior-flat-paint :last-assessed "2026-07-14"}})

    (step! "maintenance window on a verified disperser (approved)" "t2"
           {:op :schedule-maintenance :effect :propose :subject "mnt-1"
            :value {:equipment-id "disperser-001" :maintenance-type :blade-inspection
                    :scheduled-date "2026-08-01" :actuate-line? false}}
           true)

    (step! "safety concern (always high-stakes, approved)" "t3"
           {:op :flag-safety-concern :effect :propose :subject "concern-1"
            :value {:equipment-id "disperser-001" :severity :moderate
                    :description "分散工程周辺の溶剤VOC蒸気濃度上昇"}}
           true)

    (step! "shipment within the batch's own logged weight (approved)" "t4"
           {:op :coordinate-shipment :effect :propose :subject "ship-1"
            :value {:batch-id "batch-001" :weight-kg 500.0
                    :destination "buyer-yard-north"}}
           true)

    (step! "request :effect is not :propose" "t5"
           {:op :log-production-batch :effect :direct-write :subject "batch-001"
            :patch {:product-type :interior-flat-paint}})

    (step! "unrecognized op" "t6"
           {:op :actuate-disperser :effect :propose :subject "batch-001"})

    (step! "maintenance on an UNVERIFIED bead mill" "t7"
           {:op :schedule-maintenance :effect :propose :subject "mnt-2"
            :value {:equipment-id "mill-002" :maintenance-type :media-inspection
                    :scheduled-date "2026-08-01" :actuate-line? false}})

    (step! "shipment against an UNVERIFIED batch" "t8"
           {:op :coordinate-shipment :effect :propose :subject "ship-2"
            :value {:batch-id "batch-003" :weight-kg 1000.0
                    :destination "buyer-yard-south"}})

    (step! "shipment beyond the batch's own logged weight" "t9"
           {:op :coordinate-shipment :effect :propose :subject "ship-3"
            :value {:batch-id "batch-002" :weight-kg 1000.0
                    :destination "buyer-yard-east"}})

    (step! "direct line actuation attempt (PERMANENT block)" "t10"
           {:op :schedule-maintenance :effect :propose :subject "mnt-3"
            :value {:equipment-id "disperser-001" :maintenance-type :force-run
                    :scheduled-date "2026-09-01" :actuate-line? true}})

    (step! "same maintenance window scheduled twice" "t11"
           {:op :schedule-maintenance :effect :propose :subject "mnt-1"
            :value {:equipment-id "disperser-001" :maintenance-type :blade-inspection
                    :scheduled-date "2026-08-01" :actuate-line? false}})

    (step! "fabricated product-type" "t12"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:product-type :unobtainium-coating}})

    (step! "implausible viscosity reading" "t13"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:viscosity-cp -5.0}})

    (step! "implausible fineness-of-grind reading" "t14"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:fineness-of-grind-hegman 12.0}})

    (step! "VOC content above the product type's regulatory ceiling" "t15"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:voc-content-g-per-l 400.0}})

    {:db db :runs @runs}))

;; ----------------------------- render helpers -----------------------------

(defn- esc
  "Minimal HTML-escape -- every interpolated value passes through this."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- yes-no [b]
  (if b "yes" "<span class=\"critical\">no</span>"))

(defn- em-dash-or [v]
  (if (some? v) (esc v) "&mdash;"))

(defn- kw-list
  "A live set rendered as a sorted, escaped, comma-separated code list."
  [s]
  (str/join ", " (for [k (sort s)] (str "<code>" (esc k) "</code>"))))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= subject-id (:subject %)) ledger)))

(defn- status-cell [fact]
  (cond
    (nil? fact)                       ["muted" "no activity"]
    (= :committed (:t fact))          ["ok" "committed"]
    (= :governor-hold (:t fact))
    ["err" (str "governor-hold: "
                (str/join "," (map name (or (:basis fact) []))))]
    (= :approval-rejected (:t fact))  ["err" "approval-rejected"]
    :else                             ["muted" "in progress"]))

(defn- table
  "header-cells -> row-strings. Keeps every table's shape identical."
  [headers rows empty-label]
  (str "<table>\n<thead><tr>"
       (str/join (for [h headers] (str "<th>" h "</th>")))
       "</tr></thead>\n<tbody>\n"
       (if (seq rows)
         (str/join "\n" rows)
         (str "<tr><td colspan=\"" (count headers) "\" class=\"muted\">"
              empty-label "</td></tr>"))
       "\n</tbody></table>"))

;; ----------------------------- tables -----------------------------

(defn- batches-table
  "Read back OUT of the store after the run -- `:shipped-weight-kg` and
  `:last-assessed` reflect what the run actually committed."
  [db]
  (let [ledger (store/ledger db)]
    (table ["id" "product-type" "material" "weight (kg)" "viscosity (cP)"
            "VOC (g/L)" "VOC ceiling (g/L)" "Hegman" "verified?" "registered?"
            "shipped (kg)" "last assessed" "last ledger disposition"]
           (for [b (store/all-batches db)
                 :let [[cls label] (status-cell (last-fact-for ledger (:id b)))]]
             (str "<tr>"
                  "<td><code>" (esc (:id b)) "</code></td>"
                  "<td><code>" (esc (:product-type b)) "</code></td>"
                  "<td>" (em-dash-or (:material b)) "</td>"
                  "<td class=\"num\">" (em-dash-or (:weight-kg b)) "</td>"
                  "<td class=\"num\">" (em-dash-or (:viscosity-cp b)) "</td>"
                  "<td class=\"num\">" (em-dash-or (:voc-content-g-per-l b)) "</td>"
                  "<td class=\"num\">"
                  (em-dash-or (registry/voc-limit-for (:product-type b))) "</td>"
                  "<td class=\"num\">" (em-dash-or (:fineness-of-grind-hegman b)) "</td>"
                  "<td>" (yes-no (:verified? b)) "</td>"
                  "<td>" (yes-no (:registered? b)) "</td>"
                  "<td class=\"num\">" (em-dash-or (:shipped-weight-kg b)) "</td>"
                  "<td>" (em-dash-or (:last-assessed b)) "</td>"
                  "<td class=\"" cls "\">" (esc label) "</td>"
                  "</tr>"))
           "no batches")))

(defn- equipment-table [db]
  (table ["id" "kind" "verified?" "registered?" "last maintenance"
          "last scheduled maintenance"]
         (for [e (store/all-equipment db)]
           (str "<tr>"
                "<td><code>" (esc (:id e)) "</code></td>"
                "<td><code>" (esc (:kind e)) "</code></td>"
                "<td>" (yes-no (:verified? e)) "</td>"
                "<td>" (yes-no (:registered? e)) "</td>"
                "<td>" (em-dash-or (:last-maintenance-date e)) "</td>"
                "<td>" (em-dash-or (:last-scheduled-maintenance-date e)) "</td>"
                "</tr>"))
         "no equipment"))

(defn- scenario-table
  "One row per graph run -- the real final `:disposition` and the real
  governor `:violations` rule keywords."
  [runs]
  (table ["#" "scenario" "op" "subject" "confidence" "disposition"
          "hard?" "governor rules fired"]
         (for [[i {:keys [label tid request state]}] (map-indexed vector runs)
               :let [verdict (:verdict state)
                     disposition (:disposition state)
                     rules (mapv :rule (:violations verdict))]]
           (str "<tr>"
                "<td class=\"num\">" (inc i) "</td>"
                "<td>" (esc label) " <span class=\"muted\">(" (esc tid) ")</span></td>"
                "<td><code>" (esc (:op request)) "</code></td>"
                "<td><code>" (esc (:subject request)) "</code></td>"
                "<td class=\"num\">" (em-dash-or (:confidence verdict)) "</td>"
                "<td class=\""
                (case disposition :commit "ok" :hold "err" "warn")
                "\">" (esc disposition) "</td>"
                "<td>" (if (:hard? verdict)
                         "<span class=\"critical\">HARD</span>" "&mdash;") "</td>"
                "<td>" (if (seq rules)
                         (str/join ", " (for [r rules]
                                          (str "<code>" (esc r) "</code>")))
                         "&mdash;") "</td>"
                "</tr>"))
         "no runs"))

(defn- safety-concerns-table [db]
  (table ["id" "equipment" "severity" "description" "approved by"]
         (for [c (store/safety-concerns db)]
           (str "<tr>"
                "<td><code>" (esc (:id c)) "</code></td>"
                "<td><code>" (esc (:equipment-id c)) "</code></td>"
                "<td>" (esc (:severity c)) "</td>"
                "<td>" (esc (:description c)) "</td>"
                "<td>" (em-dash-or (:approved-by c)) "</td>"
                "</tr>"))
         "none"))

(defn- draft-records-table [db]
  (table ["record_id" "kind" "maintenance_id / shipment_id" "equipment_id"
          "immutable"]
         (for [r (concat (store/maintenance-history db)
                         (store/shipment-history db))]
           (str "<tr>"
                "<td><code>" (esc (get r "record_id")) "</code></td>"
                "<td>" (esc (get r "kind")) "</td>"
                "<td><code>" (esc (or (get r "maintenance_id")
                                      (get r "shipment_id"))) "</code></td>"
                "<td>" (if-let [e (get r "equipment_id")]
                         (str "<code>" (esc e) "</code>") "&mdash;") "</td>"
                "<td>" (esc (get r "immutable")) "</td>"
                "</tr>"))
         "none"))

(defn- observed-stakes
  "op -> sorted set of the `:stake` values this run's REAL proposals
  actually declared. Read out of each run's own final state, so the
  high-stakes column below is live evidence, not prose."
  [runs]
  (reduce (fn [m {:keys [request state]}]
            (if-let [stake (get-in state [:proposal :stake])]
              (update m (:op request) (fnil conj (sorted-set)) stake)
              m))
          {}
          runs))

(defn- action-gate-table
  "Derived entirely from live values: `paintmfg.governor/allowed-ops`
  (the closed op allowlist), `paintmfg.phase/phases` at
  `default-phase` (`:writes` / `:auto`), and the `:stake` each op's own
  proposal declared during this run checked against
  `paintmfg.governor/high-stakes`. Nothing here is a hand-written row."
  [runs]
  (let [ph (get phase/phases phase/default-phase)
        stakes (observed-stakes runs)]
    (table ["op" (str "phase-" phase/default-phase " write allowed?")
            "auto-eligible?" "stake observed this run"
            "always escalates (high-stakes)?"]
           (for [o (sort governor/allowed-ops)
                 :let [seen (get stakes o)]]
             (str "<tr>"
                  "<td><code>" (esc o) "</code></td>"
                  "<td>" (if (contains? (:writes ph) o)
                           "yes" "<span class=\"warn\">no</span>") "</td>"
                  "<td>" (if (contains? (:auto ph) o)
                           "<span class=\"ok\">yes</span>" "no") "</td>"
                  "<td>" (if (seq seen) (kw-list seen) "&mdash;") "</td>"
                  "<td>" (if (seq (filter governor/high-stakes seen))
                           "<span class=\"critical\">yes</span>" "no") "</td>"
                  "</tr>"))
           "no ops")))

(defn- voc-ceiling-table
  "`paintmfg.registry/voc-limit-g-per-l` itself -- the closed regulatory
  ceiling table `voc-content-exceeds-limit?` recomputes against."
  []
  (table ["product-type" "VOC ceiling (g/L)"]
         (for [pt (sort (keys registry/voc-limit-g-per-l))]
           (str "<tr>"
                "<td><code>" (esc pt) "</code></td>"
                "<td class=\"num\">" (esc (registry/voc-limit-for pt)) "</td>"
                "</tr>"))
         "no ceilings"))

(defn- hold-rules-table
  "The distinct HARD rules this run actually fired, counted from the
  ledger's own `:basis` keywords -- sorted for determinism."
  [db]
  (let [counts (frequencies (mapcat :basis (filter #(= :governor-hold (:t %))
                                                   (store/ledger db))))]
    (table ["rule" "times fired this run"]
           (for [[rule n] (sort-by (comp str key) counts)]
             (str "<tr>"
                  "<td><code>" (esc rule) "</code></td>"
                  "<td class=\"num\">" n "</td>"
                  "</tr>"))
           "no holds")))

(defn- audit-ledger-table [db]
  (table ["#" "t" "op" "actor" "subject" "disposition" "basis / rule" "summary"]
         (for [[i f] (map-indexed vector (store/ledger db))]
           (str "<tr>"
                "<td class=\"num\">" (inc i) "</td>"
                "<td>" (esc (:t f)) "</td>"
                "<td><code>" (esc (:op f)) "</code></td>"
                "<td><code>" (esc (:actor f)) "</code></td>"
                "<td><code>" (esc (:subject f)) "</code></td>"
                "<td class=\""
                (case (:disposition f) :commit "ok" :hold "err" "muted")
                "\">" (esc (:disposition f)) "</td>"
                "<td>" (if (seq (:basis f))
                         (str/join ", " (for [b (:basis f)]
                                          (str "<code>" (esc b) "</code>")))
                         "&mdash;") "</td>"
                "<td>" (if-let [s (:summary f)]
                         (esc s)
                         (esc (str/join " / " (map :detail (:violations f)))))
                "</td>"
                "</tr>"))
         "empty ledger"))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the full operator-console document from `run-demo!`'s result."
  [{:keys [db runs]}]
  (let [ph (get phase/phases phase/default-phase)]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<meta name=\"color-scheme\" content=\"light\">\n"
     "<title>cloud-itonami-isic-2022 &middot; paints / varnishes / printing ink / mastics plant ops</title>\n"
     "<style>"
     (jp-go-dds.skin/dds+skin)
     "</style>\n"
     "</head>\n<body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Paint &amp; Coatings Plant Operations Governor — Operator Console (ISIC 2022)</h1>\n"
     "  <p class=\"subtitle\">read-only sample · governor-gated · phase "
     phase/default-phase " (" (esc (:label ph)) ")"
     " · confidence floor " (esc governor/confidence-floor)
     " · direct dispersion/tinting/filling-line actuation permanently blocked</p>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Scenario (this run)</h2>\n"
     "    <p class=\"muted\">Build-time output of <code>paintmfg.render-html</code> (<code>clojure -M:dev:render-html</code>): each row is one real <code>langgraph.graph/run*</code> through <code>paintmfg.operation</code>. Approvals are real resumes of the <code>:request-approval</code> interrupt. Every batch/equipment id referenced below is a <code>paintmfg.store/sample-data!</code> seed id.</p>\n"
     (scenario-table runs) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Production batches (store state AFTER the run)</h2>\n"
     "    <p class=\"muted\">Read back out of <code>paintmfg.store</code>, not copied from the requests — <code>shipped (kg)</code> and <code>last assessed</code> moved because commits really happened. The VOC ceiling column is <code>paintmfg.registry/voc-limit-for</code> applied to each batch's own recorded product type.</p>\n"
     (batches-table db) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Equipment (dispersion / milling / tinting / filling)</h2>\n"
     "    <p class=\"muted\">An UNVERIFIED or unregistered unit can never have maintenance scheduled against it — <code>equipment-not-verified</code> is recomputed by the governor from these very fields, never taken from the advisor's rationale.</p>\n"
     (equipment-table db) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Safety concerns (flagged this run)</h2>\n"
     "    <p class=\"muted\">Always human-approved — <code>:coordination/safety-concern</code> is in <code>paintmfg.governor/high-stakes</code>, and <code>:flag-safety-concern</code> is in no phase's <code>:auto</code> set. Two independent layers agree.</p>\n"
     (safety-concerns-table db) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Committed draft records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only (<code>paintmfg.registry</code>) — this actor never actuates a mixing/dispersion line and never dispatches a real freight carrier.</p>\n"
     (draft-records-table db) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate</h2>\n"
     "    <p class=\"muted\">Derived from live values: <code>paintmfg.governor/allowed-ops</code>, <code>paintmfg.phase/phases</code> at phase "
     phase/default-phase ", and the <code>:stake</code> each op's own proposal declared during this run.</p>\n"
     (action-gate-table runs) "\n"
     "    <p class=\"muted\">Closed proposal-effect allowlist: " (kw-list governor/allowed-proposal-effects)
     ". High-stakes set: " (kw-list governor/high-stakes)
     ". Phase-" phase/default-phase " writes: " (kw-list (:writes ph))
     ". Phase-" phase/default-phase " auto: " (kw-list (:auto ph))
     ". All write ops: " (kw-list phase/write-ops) ".</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD holds fired (this run)</h2>\n"
     "    <p class=\"muted\">Counted from the ledger's own <code>:basis</code> rule keywords. A HARD hold cannot be overridden by any phase or any human approval — <code>line-actuate-blocked</code> and <code>line-control-blocked</code> are the two permanent scope boundaries.</p>\n"
     (hold-rules-table db) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>VOC-content regulatory ceilings</h2>\n"
     "    <p class=\"muted\"><code>paintmfg.registry/voc-limit-g-per-l</code> — the closed table <code>voc-content-exceeds-limit?</code> independently recomputes against (modeled on the U.S. EPA AIM VOC-content-limit framework / EU Decopaint Directive 2004/42/EC; representative, not an exhaustive multi-jurisdiction database).</p>\n"
     (voc-ceiling-table) "\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit and every hold this scenario produced, in order.</p>\n"
     (audit-ledger-table db) "\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>Generated at build time by <code>paintmfg.render-html</code> from a real actor run. Deterministic: no timestamps, no randomness — two consecutive runs are byte-identical.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        html (render result)
        f (java.io.File. out)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit out html)
    (println "wrote" out
             (str "(" (count runs) " runs, "
                  (count (store/ledger db)) " ledger facts, "
                  (count (store/maintenance-history db)) " maintenance drafts, "
                  (count (store/shipment-history db)) " shipment drafts, "
                  (count (store/safety-concerns db)) " safety concerns)"))))
