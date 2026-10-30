-- Tier tables: one integer per model, higher is faster. The key is a normalized model string.
create table cpu_tier (
    key  varchar(60)  primary key,
    name varchar(100) not null,
    tier int          not null check (tier > 0)
);

create table gpu_tier (
    key  varchar(60)  primary key,
    name varchar(100) not null,
    tier int          not null check (tier > 0)
);

create table game (
    slug varchar(100) primary key check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name varchar(200) not null
);

-- A null column means the game states no requirement.
create table game_requirement (
    game_slug      varchar(100) not null references game (slug),
    level          varchar(11)  not null check (level in ('minimum', 'recommended')),
    cpu_key        varchar(60)  references cpu_tier (key),
    gpu_key        varchar(60)  references gpu_tier (key),
    vram_mb        int check (vram_mb > 0),
    ram_mb         int check (ram_mb > 0),
    disk_mb        int check (disk_mb > 0),
    vulkan_version varchar(10) check (vulkan_version ~ '^[0-9]{1,9}\.[0-9]{1,9}(\.[0-9]{1,9})?$'),
    primary key (game_slug, level)
);
