-- V1: initial schema for the mortgage pricing exception service.

create table app_user (
    id   varchar(64) not null primary key,
    name varchar(255) not null,
    role varchar(32) not null
);

create table mortgage_application (
    id                 varchar(64) not null primary key,
    applicant_name     varchar(255) not null,
    standard_rate_bps  int not null
);

create table pricing_exception_request (
    id                       uuid not null primary key,
    application_id           varchar(64) not null references mortgage_application(id),
    requested_discount_bps   int not null,
    reason                   varchar(2000) not null,
    status                   varchar(32) not null,
    created_by_user_id       varchar(64) not null references app_user(id),
    created_at               timestamp not null,
    decided_by_user_id       varchar(64) references app_user(id),
    decided_at               timestamp,
    decision_reason          varchar(2000),
    version                  bigint not null default 0,
    -- Computed column that is non-null only for PENDING rows. Combined with the unique index
    -- below, this enforces "at most one open (PENDING) request per application" at the database
    -- level (NULLs are not considered equal by unique indexes, so terminal-status rows are
    -- unconstrained). This backs the application-level pre-check in PricingExceptionRequestService
    -- and is the safeguard that wins any true concurrent-create race.
    pending_application_id   varchar(64) as (case when status = 'PENDING' then application_id else null end)
);

create index idx_per_application_id on pricing_exception_request(application_id);
create index idx_per_status on pricing_exception_request(status);
create unique index idx_one_pending_per_application on pricing_exception_request(pending_application_id);

create table request_history_event (
    id             bigint not null auto_increment primary key,
    request_id     uuid not null references pricing_exception_request(id),
    event_type     varchar(32) not null,
    actor_user_id  varchar(64) not null references app_user(id),
    timestamp      timestamp not null,
    notes          varchar(2000)
);

create index idx_rhe_request_id on request_history_event(request_id);

-- Idempotency is scoped per caller: the same key value may be reused independently by two
-- different relationship managers without colliding (see manager_id in the composite PK).
create table idempotency_record (
    manager_id         varchar(64) not null,
    idempotency_key    varchar(255) not null,
    request_body_hash  varchar(64) not null,
    http_status        int not null,
    response_body      varchar(4000) not null,
    created_at         timestamp not null,
    primary key (manager_id, idempotency_key)
);
