(ns paintmfg.registry-test
  (:require [clojure.test :refer [deftest is]]
            [paintmfg.registry :as r]))

;; ----------------------------- equipment-verified? / equipment-registered? / equipment-ready? -----------------------------

(deftest equipment-is-verified-when-flagged
  (is (true? (r/equipment-verified? {:id "e1" :verified? true}))))

(deftest equipment-is-not-verified-when-false-or-missing
  (is (false? (r/equipment-verified? {:id "e1" :verified? false})))
  (is (false? (r/equipment-verified? {:id "e1"}))))

(deftest equipment-is-registered-when-flagged
  (is (true? (r/equipment-registered? {:registered? true}))))

(deftest equipment-is-not-registered-when-false-or-missing
  (is (false? (r/equipment-registered? {:registered? false})))
  (is (false? (r/equipment-registered? {}))))

(deftest equipment-ready-requires-both
  (is (true? (r/equipment-ready? {:verified? true :registered? true})))
  (is (false? (r/equipment-ready? {:verified? true :registered? false})))
  (is (false? (r/equipment-ready? {:verified? false :registered? true})))
  (is (false? (r/equipment-ready? {}))))

;; ----------------------------- batch-verified? / batch-registered? / batch-ready? -----------------------------

(deftest batch-is-verified-when-flagged
  (is (true? (r/batch-verified? {:id "b1" :verified? true}))))

(deftest batch-is-not-verified-when-false-or-missing
  (is (false? (r/batch-verified? {:id "b1" :verified? false})))
  (is (false? (r/batch-verified? {:id "b1"}))))

(deftest batch-is-registered-when-flagged
  (is (true? (r/batch-registered? {:registered? true}))))

(deftest batch-is-not-registered-when-false-or-missing
  (is (false? (r/batch-registered? {:registered? false})))
  (is (false? (r/batch-registered? {}))))

(deftest batch-ready-requires-both
  (is (true? (r/batch-ready? {:verified? true :registered? true})))
  (is (false? (r/batch-ready? {:verified? true :registered? false})))
  (is (false? (r/batch-ready? {:verified? false :registered? true})))
  (is (false? (r/batch-ready? {}))))

;; ----------------------------- shipment-weight-exceeded? -----------------------------

(deftest small-shipment-within-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 5000.0 :shipped-weight-kg 1000.0} 500.0))))

(deftest shipment-that-pushes-past-weight-exceeds
  (is (true? (r/shipment-weight-exceeded?
              {:weight-kg 8000.0 :shipped-weight-kg 7500.0} 1000.0))))

(deftest shipment-exactly-at-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 8000.0 :shipped-weight-kg 7500.0} 500.0))
      "exactly at weight is not over, only strictly beyond"))

(deftest missing-weight-is-not-flagged-exceeded
  (is (false? (r/shipment-weight-exceeded? {} 100.0)))
  (is (false? (r/shipment-weight-exceeded? {:weight-kg 800.0} nil))))

;; ----------------------------- product-type-valid? -----------------------------

(deftest known-product-types-are-valid
  (doseq [g [:interior-flat-paint :interior-non-flat-paint :exterior-paint
             :varnish :lacquer :primer-sealer-undercoat
             :industrial-coating :marine-coating
             :offset-printing-ink :flexographic-printing-ink :gravure-printing-ink
             :mastic :sealant :caulk]]
    (is (r/product-type-valid? g))))

(deftest fabricated-product-type-is-invalid
  (is (not (r/product-type-valid? :unobtainium-coating)))
  (is (not (r/product-type-valid? nil))))

;; ----------------------------- viscosity-valid? -----------------------------

(deftest typical-viscosity-is-valid
  (is (r/viscosity-valid? 9500.0))
  (is (r/viscosity-valid? 0.0))
  (is (r/viscosity-valid? 45000.0))
  (is (r/viscosity-valid? 1000000.0)))

(deftest negative-viscosity-is-invalid
  (is (not (r/viscosity-valid? -5.0))))

(deftest excessive-viscosity-is-invalid
  (is (not (r/viscosity-valid? 9999999.0)))
  (is (not (r/viscosity-valid? 1000000.01))))

(deftest non-numeric-or-missing-viscosity-is-invalid
  (is (not (r/viscosity-valid? nil)))
  (is (not (r/viscosity-valid? "9500"))))

;; ----------------------------- fineness-of-grind-valid? -----------------------------

(deftest typical-fineness-of-grind-is-valid
  (is (r/fineness-of-grind-valid? 6.5))
  (is (r/fineness-of-grind-valid? 0.0))
  (is (r/fineness-of-grind-valid? 8.0)))

(deftest out-of-range-fineness-of-grind-is-invalid
  (is (not (r/fineness-of-grind-valid? -1.0)))
  (is (not (r/fineness-of-grind-valid? 12.0)))
  (is (not (r/fineness-of-grind-valid? 8.01))))

(deftest non-numeric-or-missing-fineness-of-grind-is-invalid
  (is (not (r/fineness-of-grind-valid? nil)))
  (is (not (r/fineness-of-grind-valid? "6.5"))))

;; ----------------------------- voc-limit-for / voc-content-exceeds-limit? -----------------------------

(deftest every-valid-product-type-has-a-voc-limit
  (doseq [pt r/valid-product-types]
    (is (some? (r/voc-limit-for pt))
        (str pt " must have a VOC-content regulatory ceiling"))))

(deftest voc-content-within-limit-does-not-exceed
  (is (false? (r/voc-content-exceeds-limit? :interior-flat-paint 35.0))))

(deftest voc-content-exactly-at-limit-does-not-exceed
  (is (false? (r/voc-content-exceeds-limit? :interior-flat-paint 50.0))
      "exactly at the ceiling is not over, only strictly beyond"))

(deftest voc-content-over-limit-exceeds
  (is (true? (r/voc-content-exceeds-limit? :interior-flat-paint 400.0))))

(deftest missing-voc-content-is-never-flagged-exceeded
  (is (false? (r/voc-content-exceeds-limit? :interior-flat-paint nil))))

(deftest unknown-product-type-with-no-ceiling-is-never-flagged-exceeded-here
  (is (false? (r/voc-content-exceeds-limit? :unobtainium-coating 999.0))
      "invalid-product-type-violations in the governor rejects a fabricated type on its own"))

;; ----------------------------- register-maintenance -----------------------------

(deftest maintenance-is-a-draft-not-a-real-actuation
  (let [result (r/register-maintenance "mnt-1" "disperser-001" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-assigns-maintenance-number
  (let [result (r/register-maintenance "mnt-1" "disperser-001" 7)]
    (is (= (get result "maintenance_number") "MNT-000007"))
    (is (= (get-in result ["record" "maintenance_id"]) "mnt-1"))
    (is (= (get-in result ["record" "equipment_id"]) "disperser-001"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "" "disperser-001" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "disperser-001" -1))))

;; ----------------------------- register-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-maintenance "mnt-1" "disperser-001" 0)
        hist (r/append [] c1)
        c2 (r/register-maintenance "mnt-2" "disperser-001" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "MNT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "MNT-000001" (get-in hist2 [1 "record_id"])))))
