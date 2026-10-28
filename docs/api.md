# Specs API

The specs API stores hardware reports from `detect/detect.sh`. It gives each report an anonymous code. You use the code to read the report again.

Phase 2 stores schema v1 reports only. Phase 3 adds game checks. Use `GET /api/games` to list the games. Use `GET /api/specs/{code}/games/{slug}` to compare a report with a game.

## Endpoints

### POST /api/specs

Send one schema v1 report. The body must match schema v1. See [What the script collects](detect.md).

Success response: `201 Created`

```json
{
  "code": "K7Q2M9XD4P",
  "expiresAt": "2026-11-26T10:00:00Z"
}
```

- `code`: 10 characters. Crockford base32 (`0-9`, `A-Z` without `I`, `L`, `O`, `U`). The code is random.
- `expiresAt`: ISO-8601 instant. The report expires 30 days after creation.
- `Location` header: `/api/specs/{code}`

### GET /api/specs/{code}

Read a stored report.

The code is upper-case only. A lower-case code gives `404 Not Found`.

Success response: `200 OK`. The body is the stored schema v1 report. The values are the same as in the posted report. The key order and the whitespace can change.

Not found: `404 Not Found`. The code is unknown or expired.

### GET /api/games

List the games in the seed data.

Success response: `200 OK`. The body is an array. The games are sorted by `name`. The list has no paging.

```json
[
  { "slug": "cyberpunk-2077", "name": "Cyberpunk 2077" }
]
```

- `slug`: the game id. Lower case letters, digits and single hyphens.
- `name`: the game name.

### GET /api/specs/{code}/games/{slug}

Compare a stored report with the requirements of one game.

- `code`: the same code as `GET /api/specs/{code}`. Unknown or expired codes give `404 SPECS_NOT_FOUND`.
- `slug`: the game slug from `GET /api/games`. It must match `^[a-z0-9]+(-[a-z0-9]+)*$` and have 100 characters or less. A malformed or unknown slug gives `404 GAME_NOT_FOUND`.

Success response: `200 OK`

```json
{
  "game": { "slug": "cyberpunk-2077", "name": "Cyberpunk 2077" },
  "minimum": {
    "result": "fail",
    "parts": {
      "cpu":    { "result": "pass", "required": "Intel Core i7-6700" },
      "gpu":    { "result": "pass", "required": "NVIDIA GeForce GTX 1060 6GB" },
      "vram":   { "result": "pass", "required": 6144 },
      "ram":    { "result": "pass", "required": 12288 },
      "disk":   { "result": "pass", "required": 71680 },
      "vulkan": { "result": "unknown", "required": "1.3" }
    }
  },
  "recommended": { "...": "same shape as minimum" }
}
```

- `game`: the game slug and name.
- `minimum` and `recommended`: the result for each level.
- `result`: `pass`, `fail` or `unknown`.
- `parts`: the result for each part: `cpu`, `gpu`, `vram`, `ram`, `disk` and `vulkan`.
- `required`: the requirement from the game data.
  - `cpu` and `gpu`: the tier name.
  - `vram`, `ram` and `disk`: the amount in MB.
  - `vulkan`: the version string.
  - `null`: the game states no requirement for this part.

The response does not repeat your values. Your client has the report.

#### Compare rules

For each level and each part:

- The requirement is `null`: the part is `pass`.
- `cpu`: `pass` if your CPU tier is at least the required tier. `unknown` if your CPU has no tier.
- `gpu`: `pass` if any GPU in the report has a tier at least the required tier. `unknown` if no GPU matches and at least one GPU has no tier. `fail` in all other cases. No GPU in the report gives `unknown`.
- `vram`: `pass` if any GPU has `vramMb` at least 90% of the required amount. `unknown` if no GPU passes and at least one GPU has no VRAM value. `fail` in all other cases.
- `ram`: `pass` if `ramMb` is at least 90% of the required amount. `unknown` if `ramMb` is `null`.
- `disk`: `pass` if `diskFreeMb` is at least the required amount. `unknown` if `diskFreeMb` is `null`. The disk check has no tolerance.
- `vulkan`:
  - `supported` is `false`: `fail`.
  - `supported` is `null`: `unknown`.
  - `apiVersion` does not match `major.minor` or `major.minor.patch`: `unknown`.
  - Otherwise the result compares `major.minor` with the required version. For example, `1.3.275` meets `1.3`. `1.2` does not meet `1.3`.
- Level result: `fail` if any part is `fail`. Otherwise `unknown` if any part is `unknown`. Otherwise `pass`.

Why the RAM and VRAM check has a 10% tolerance: the operating system and the driver do not report the full installed size. For example, a 16 GB machine reports `MemTotal` 15163 MB. Without the tolerance, that machine would fail a 16 GB requirement. With the tolerance, a 6 GB GPU still fails an 8 GB VRAM requirement.

Not checked:

- CPU cores and threads.
- CPU or GPU bottleneck.
- The disk of the Steam library. The report has the home disk only.

#### Errors

| Status | `error` | When |
|---|---|---|
| `404` | `SPECS_NOT_FOUND` | The code is unknown, expired or malformed. |
| `404` | `GAME_NOT_FOUND` | The slug is unknown or malformed. The message is fixed. |
| `405` | `METHOD_NOT_ALLOWED` | The method is not `GET`. The message is `Request failed.` |
| `500` | `INTERNAL_ERROR` | The server has an unexpected error. The message does not show the cause. |

See [Games and tiers](games.md) for the data that the compare uses.

### API description

- `GET /swagger-ui.html`: an interactive page that shows the endpoints.
- `GET /v3/api-docs`: the OpenAPI description as JSON.

## Errors

Every error uses the same body shape:

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request body is not valid.",
  "timestamp": "2026-10-27T10:00:00Z"
}
```

- `status`: the HTTP status code.
- `error`: a fixed code. The code does not change.
- `message`: a short text. The message never repeats your input.
- `timestamp`: ISO-8601 instant.

| Status | `error` | When |
|---|---|---|
| `400` | `MALFORMED_JSON` | The body is not valid JSON. |
| `400` | `VALIDATION_FAILED` | The `schemaVersion` is unknown. A field has the wrong type. The body has an unknown field. A value is out of range. |
| `404` | `SPECS_NOT_FOUND` | The code is unknown or expired. |
| `404` | `GAME_NOT_FOUND` | The game slug is unknown or malformed. The message is fixed. |
| `404` | `NOT_FOUND` | The path is unknown. The message is `Request failed.` |
| `405` | `METHOD_NOT_ALLOWED` | The path does not accept the HTTP method. The message is `Request failed.` |
| `413` | `PAYLOAD_TOO_LARGE` | The body is larger than 16 KB (16384 bytes). |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | The `Content-Type` is not `application/json`. The message is `Request failed.` |
| `500` | `INTERNAL_ERROR` | The server has an unexpected error. The message does not show the cause. |

Limits for `POST /api/specs`:

- Strings: 200 characters maximum.
- `gpus`: 8 items maximum.
- Numbers: 0 or more.
- Number maximums: `cpu.cores` and `cpu.threads` 4096, `ramMb` 16777216, `diskFreeMb` 1000000000, `gpus[].vramMb` 1048576.
- Numbers must be JSON integers. Strings such as `"123"` and decimals such as `1.5` are rejected.
- Strings must be JSON strings. They must not contain the NUL character (`\u0000`). They must not contain a lone surrogate (an unpaired `\ud800` to `\udfff` escape). A valid surrogate pair is accepted.
- Nullable fields may be `null`. For example, `null` means a tool was missing.
- Unknown fields are rejected.

## Privacy and retention

- The API does not store your IP address.
- The API does not store your user agent.
- The API does not need an account or a login.
- A daily job deletes reports 30 days after creation.

## Example

Run the script, send the report, and save the code:

```bash
CODE=$(bash detect/detect.sh | curl -sS -X POST localhost:8080/api/specs \
  -H 'Content-Type: application/json' --data-binary @- | jq -r .code)
echo "$CODE"
```

Read the report with the code:

```bash
curl -sS localhost:8080/api/specs/$CODE
```

The examples need `jq`. The API must run on `localhost:8080`. See the [README](../README.md) for the backend run steps.
