# Governance

`cloud-itonami-isic-2022` is an OSS open-business blueprint for paints, varnishes and similar coatings, printing ink and mastics plant operations coordination.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a mixing/dispersion-equipment or tinting/filling-line action the governor refuses is never dispatched to hardware.
- the Paint & Coatings Plant Operations Governor remains independent of the advisor.
- hard policy violations (equipment-control bypass, line actuation, record-suppression, unauthorized disclosure) cannot be overridden by human approval.
- every schedule, sign-off, record and disclose path is auditable.
- sensitive operating and personal data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing mixing/dispersion-line-control or record policy checks
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
