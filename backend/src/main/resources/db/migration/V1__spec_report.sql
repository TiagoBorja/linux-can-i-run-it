create table spec_report (
    id             uuid primary key,
    code           varchar(10) not null unique,
    payload        jsonb       not null,
    schema_version integer     not null,
    created_at     timestamptz not null,
    expires_at     timestamptz not null
);

create index idx_spec_report_expires_at on spec_report (expires_at);
