# Can I Run It — Linux

Check if a game runs on your Linux PC. No login. No tracking.

A small open-source Bash script reads your hardware specs and prints them as JSON. You decide if you send them.

Status: work in progress.

## Usage

Run the script from the repository root:

```bash
bash detect/detect.sh
```

It prints your hardware specs as JSON. It does not send data. See [What the script collects](docs/detect.md).

## Backend

The backend stores specs under an anonymous code. It needs Java 21 and Docker.

```bash
cd backend && docker compose up -d && ./mvnw spring-boot:run
```

Run the tests with `./mvnw verify`. The tests need Docker for Testcontainers.

See [Specs API](docs/api.md).
