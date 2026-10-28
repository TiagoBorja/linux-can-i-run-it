# Specs API

The specs API stores hardware reports from `detect/detect.sh`. It gives each report an anonymous code. You use the code to read the report again.

Phase 2 stores schema v1 reports only. Phase 3 adds game checks. The API does not compare games yet.

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
- Strings must be JSON strings. They must not contain the NUL character (`\u0000`).
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
