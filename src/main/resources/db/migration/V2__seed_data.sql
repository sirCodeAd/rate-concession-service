-- V2: seed users, mortgage applications, and example requests so the workflow can be explored
-- immediately after startup without any manual setup.

insert into app_user (id, name, role) values
    ('rm-1', 'Dana Whitfield', 'RELATIONSHIP_MANAGER'),
    ('rm-2', 'Marcus Ojo', 'RELATIONSHIP_MANAGER'),
    ('rev-1', 'Priya Kapoor', 'REVIEWER'),
    ('rev-2', 'Tom Bracewell', 'REVIEWER');

insert into mortgage_application (id, applicant_name, standard_rate_bps) values
    ('app-1001', 'Alice Moreno', 625),
    ('app-1002', 'Ben Castellano', 610),
    ('app-1003', 'Chidinma Okafor', 640),
    ('app-1004', 'Diego Fernandez', 599),
    ('app-1005', 'Elin Sundberg', 655);

-- Example 1: a PENDING request awaiting review.
insert into pricing_exception_request
    (id, application_id, requested_discount_bps, reason, status, created_by_user_id, created_at, version)
values
    ('11111111-1111-1111-1111-111111111111', 'app-1001', 25,
     'Applicant has a competing offer at a lower rate from another lender.', 'PENDING', 'rm-1',
     '2025-01-06T09:15:00Z', 0);

insert into request_history_event (request_id, event_type, actor_user_id, timestamp, notes) values
    ('11111111-1111-1111-1111-111111111111', 'CREATED', 'rm-1', '2025-01-06T09:15:00Z', null);

-- Example 2: an APPROVED request, with its full history trail.
insert into pricing_exception_request
    (id, application_id, requested_discount_bps, reason, status, created_by_user_id, created_at,
     decided_by_user_id, decided_at, decision_reason, version)
values
    ('22222222-2222-2222-2222-222222222222', 'app-1002', 15,
     'Long-standing customer relationship with strong repayment history.', 'APPROVED', 'rm-2',
     '2025-01-03T14:00:00Z', 'rev-1', '2025-01-04T10:30:00Z', 'Approved given excellent credit history.', 0);

insert into request_history_event (request_id, event_type, actor_user_id, timestamp, notes) values
    ('22222222-2222-2222-2222-222222222222', 'CREATED', 'rm-2', '2025-01-03T14:00:00Z', null),
    ('22222222-2222-2222-2222-222222222222', 'APPROVED', 'rev-1', '2025-01-04T10:30:00Z',
     'Approved given excellent credit history.');

-- Example 3: a DECLINED request, with its history trail.
insert into pricing_exception_request
    (id, application_id, requested_discount_bps, reason, status, created_by_user_id, created_at,
     decided_by_user_id, decided_at, decision_reason, version)
values
    ('33333333-3333-3333-3333-333333333333', 'app-1003', 75,
     'Applicant requested a large discount citing market conditions.', 'DECLINED', 'rm-1',
     '2025-01-02T11:00:00Z', 'rev-2', '2025-01-02T16:45:00Z',
     'Requested discount exceeds what risk policy supports for this profile.', 0);

insert into request_history_event (request_id, event_type, actor_user_id, timestamp, notes) values
    ('33333333-3333-3333-3333-333333333333', 'CREATED', 'rm-1', '2025-01-02T11:00:00Z', null),
    ('33333333-3333-3333-3333-333333333333', 'DECLINED', 'rev-2', '2025-01-02T16:45:00Z',
     'Requested discount exceeds what risk policy supports for this profile.');
