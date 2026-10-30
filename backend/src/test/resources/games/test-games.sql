-- Own test rows. Tier 1 always passes, tier 1000000 always fails, so the tests do not depend on seed tiers.
insert into cpu_tier (key, name, tier) values
    ('testcpulow', 'Test CPU Low', 1),
    ('testcpuhigh', 'Test CPU High', 1000000);
insert into gpu_tier (key, name, tier) values
    ('testgpulow', 'Test GPU Low', 1),
    ('testgpuhigh', 'Test GPU High', 1000000);

-- Inserted out of name order on purpose.
insert into game (slug, name) values
    ('test-zulu', 'Test Zulu Game'),
    ('test-compare', 'Test Compare Game'),
    ('test-alpha', 'Test Alpha Game'),
    ('test-nulls', 'Test Nulls Game');

insert into game_requirement (game_slug, level, cpu_key, gpu_key, vram_mb, ram_mb, disk_mb, vulkan_version) values
    ('test-zulu', 'minimum', null, null, 1, 1, 1, null),
    ('test-zulu', 'recommended', null, null, 2, 2, 2, null),
    ('test-alpha', 'minimum', null, null, 1, 1, 1, null),
    ('test-alpha', 'recommended', null, null, 2, 2, 2, null),
    ('test-compare', 'minimum', 'testcpulow', 'testgpulow', 4096, 8192, 10000, '1.1'),
    ('test-compare', 'recommended', 'testcpuhigh', 'testgpuhigh', 8192, 16384, 200000, '1.3'),
    ('test-nulls', 'minimum', null, null, null, null, null, null),
    ('test-nulls', 'recommended', null, null, null, 20000, null, null);
