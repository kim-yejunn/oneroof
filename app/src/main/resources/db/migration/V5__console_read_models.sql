-- Read models for the console. They are views over the write-side tables, so a screen always
-- shows what was committed and nothing has to be kept in sync by hand.

CREATE VIEW period_overview AS
SELECT c.period_ym                                                              AS period,
       count(*)                                                                 AS claim_count,
       sum(c.amount_krw)                                                        AS total_krw,
       count(*) FILTER (WHERE c.settlement_status = 'PENDING')                  AS pending_count,
       count(*) FILTER (WHERE c.settlement_status = 'UNDER_APPROVAL')           AS under_approval_count,
       count(*) FILTER (WHERE c.settlement_status = 'PAYMENT_REQUESTED')        AS payment_requested_count,
       count(*) FILTER (WHERE c.settlement_status = 'PAID')                     AS paid_count,
       count(*) FILTER (WHERE c.settlement_status = 'SETTLED_IN_SOURCE')        AS settled_in_source_count,
       count(*) FILTER (WHERE c.settlement_status IN ('REJECTED', 'REJECTED_IN_SOURCE')) AS rejected_count,
       (b.id IS NOT NULL)                                                       AS batch_opened,
       b.source_event_id                                                        AS source_event_id
FROM rebate_claim c
       LEFT JOIN settlement_batch b ON b.period_ym = c.period_ym
GROUP BY c.period_ym, b.id, b.source_event_id;

CREATE VIEW review_overview AS
SELECT r.id,
       r.period_ym                                    AS period,
       r.unit_key,
       r.related_group,
       r.member_agency_ids,
       r.total_krw,
       r.limit_krw,
       r.exceeds_limit,
       r.required_approver,
       r.status,
       r.title,
       r.rationale,
       r.correlation_id,
       a.id                                           AS approval_request_id,
       a.status                                       AS approval_status,
       a.decided_by_role,
       a.decided_by_name,
       a.decided_on,
       a.decision_comment,
       (SELECT count(*) FROM rebate_claim c WHERE c.review_id = r.id) AS claim_count,
       (SELECT count(*) FROM rebate_claim c WHERE c.review_id = r.id AND c.settlement_status = 'PAID') AS paid_count
FROM rebate_review r
       LEFT JOIN approval_request a ON a.subject_type = 'RebateReview' AND a.subject_id = r.id::text;

CREATE VIEW claim_overview AS
SELECT c.rebate_id,
       c.agency_id,
       g.name               AS agency_name,
       c.period_ym          AS period,
       c.amount_krw,
       c.sales_performance_krw,
       c.original_status,
       c.original_approver,
       c.original_payment_date,
       c.settlement_status,
       c.review_id,
       c.source_ref,
       p.id                 AS payment_instruction_id,
       p.status             AS payment_status,
       p.gateway_reference,
       p.bank_reference
FROM rebate_claim c
       JOIN agency g ON g.agency_id = c.agency_id
       LEFT JOIN payment_instruction p ON p.reference_type = 'RebateClaim' AND p.reference_id = c.rebate_id;

CREATE VIEW payment_overview AS
SELECT p.id,
       p.reference_type,
       p.reference_id,
       p.agency_id,
       g.name AS agency_name,
       p.amount_krw,
       p.status,
       p.gateway_reference,
       p.bank_reference,
       p.requested_on,
       p.correlation_id,
       c.period_ym AS period,
       c.review_id
FROM payment_instruction p
       JOIN agency g ON g.agency_id = p.agency_id
       LEFT JOIN rebate_claim c ON p.reference_type = 'RebateClaim' AND c.rebate_id = p.reference_id;
