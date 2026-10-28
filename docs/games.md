# Games and tiers

The game data lives in one seed file. The API reads it for the game list and the game compare.

- File: `backend/src/main/resources/db/migration/R__seed_data.sql`
- The file is a Flyway repeatable migration. Flyway runs it again each time the file changes.
- Each run deletes all rows in foreign-key order. Then it inserts the tiers and the games. Edit the file, then run the backend again.
- The data is hand-made. No external source feeds it. Values can be wrong. Check every value before you open a PR.

## Tables

| Table | Columns | Notes |
|---|---|---|
| `cpu_tier` | `key`, `name`, `tier` | Natural key: `key`. |
| `gpu_tier` | `key`, `name`, `tier` | Natural key: `key`. |
| `game` | `slug`, `name` | Natural key: `slug`. |
| `game_requirement` | `game_slug`, `level`, `cpu_key`, `gpu_key`, `vram_mb`, `ram_mb`, `disk_mb`, `vulkan_version` | One row per game and level. `level` is `minimum` or `recommended`. |

- `tier`: an integer above 0. Higher is faster.
- `level`: only `minimum` or `recommended`.
- `game_requirement.cpu_key` and `gpu_key`: keys from the tier tables. They can be `null`.
- Numeric columns and `vulkan_version` can be `null`. `null` means the store page states no requirement.
- Each game needs both levels: `minimum` and `recommended`.
- For each numeric column and tier, `minimum` must be less than or equal to `recommended`.

## Keys

A tier key must already be normalized. The matcher compares the user model with these keys after it normalizes the model. A key that is not normalized never matches.

Rules for a key:

- Lower case only.
- No `(r)` and no `(tm)`.
- Only letters and digits. Every other character is a single space.
- Single spaces only. No leading or trailing space.

Examples:

- `rtx 5050 laptop` is valid.
- `NVIDIA RTX 5050 Laptop` is not valid.
- `radeon 680m` is valid.

The matcher finds the longest key that appears in the model as whole words. So `rtx 3060 ti` wins over `rtx 3060` for a model that says `RTX 3060 Ti`. A key `gtx 1060` does not match `gtx 10600`.

Add a key for each model that users report.

## Tier values

- The tier is our own rough ranking. We do not copy a benchmark table.
- One scale covers NVIDIA, AMD and Intel GPUs. A tier value means the same speed for every vendor.
- Higher is faster.

## Where requirements come from

- Take the requirements from the public store page of the game. Do not use memory.
- When a store page lists "Intel X or AMD Y", store the tier of the lower one (X or Y, whichever has the lower tier).
- If the store page gives no CPU or GPU, set the key to `null`.

## Add a game

1. Open the public store page of the game. Copy the minimum and recommended requirements.
2. Add a row to `game` with a new `slug`. The slug must match `^[a-z0-9]+(-[a-z0-9]+)*$`. Use the same rules as the API slug.
3. Add a `minimum` row and a `recommended` row to `game_requirement`.
4. For each CPU and GPU, use a key from the tier tables. If the key does not exist, add the tier first. See [Add a tier](#add-a-tier).
5. Run the tests. `SeedDataIT` checks the level rules and the key rules.

Example (the values are examples, not real data):

```sql
INSERT INTO game (slug, name) VALUES ('example-game', 'Example Game');

INSERT INTO game_requirement
  (game_slug, level, cpu_key, gpu_key, vram_mb, ram_mb, disk_mb, vulkan_version)
VALUES
  ('example-game', 'minimum', 'ryzen 5 3600', 'rtx 2060', 6144, 8192, 30720, '1.2'),
  ('example-game', 'recommended', 'ryzen 7 5800x', 'rtx 3070', 8192, 16384, 30720, '1.3');
```

## Add a tier

1. Pick the tier value. Compare the new model with the models already in the table. Keep the one scale for all vendors.
2. Add a row to `cpu_tier` or `gpu_tier`. Use a normalized `key`. Give a readable `name`, such as `AMD Ryzen 7 5800X`.
3. If the model is a laptop part, give it its own key. For example, `rtx 5050 laptop`.

Example (the values are examples, not real data):

```sql
INSERT INTO gpu_tier (key, name, tier) VALUES ('rtx 2060', 'NVIDIA GeForce RTX 2060', 30);
```

## Unknown models

- A user model with no matching tier gives `unknown`. It does not give `fail`.
- The compare uses the tier of the longest matching key. If no key matches, the part is `unknown`.
- To fix an `unknown` result, add the tier key for that model.

## Check your change

Run the backend tests from the `backend` folder:

```bash
cd backend && ./mvnw verify
```

The checks that cover the seed are in `SeedDataIT`. It checks that every game has both levels. It checks the level order. It checks that the sample report CPU and GPUs resolve to a tier. It checks that every tier key is normalized.

See [API](api.md) for the compare rules.
