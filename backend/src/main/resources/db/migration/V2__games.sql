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
    slug varchar(100) primary key,
    name varchar(200) not null
);

-- A null column means the game states no requirement.
create table game_requirement (
    game_slug      varchar(100) not null references game (slug),
    level          varchar(11)  not null check (level in ('minimum', 'recommended')),
    cpu_key        varchar(60)  references cpu_tier (key),
    gpu_key        varchar(60)  references gpu_tier (key),
    vram_mb        int,
    ram_mb         int,
    disk_mb        int,
    vulkan_version varchar(10),
    primary key (game_slug, level)
);
