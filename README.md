# Calculator DevOps

A small Calculator REST API built with **Spring Boot**, wrapped in a full CI/CD pipeline:
**GitHub → Jenkins → Docker Hub → Docker Compose**.

CSE 816 Software Production Engineering, Mini-Project.

- GitHub: https://github.com/ashokCh-dev/calculator-devops
- Docker Hub: https://hub.docker.com/r/ashokch11/calculator-devops

## Architecture

![CI/CD pipeline architecture](docs/architecture.png)

```
 git push ──► GitHub ──(Poll SCM)──► Jenkins pipeline
                                      │
      Checkout → Build → Test → Docker Build → Push → Deploy
                          │                     │        │
                    JUnit report          Docker Hub   docker compose up -d
                                                         │
                                                         ▼
                          client ──► nginx :8090 ──► calculator-1 :8080
                                                └──► calculator-2 :8080
```

Jenkins emails the build result (success or failure) after every run.

## Tech stack

| Part | Tool |
|------|------|
| Application | Java 21, Spring Boot 3.5 (Web, Validation, Actuator) |
| Build | Maven (wrapper included, `./mvnw`) |
| Tests | JUnit 5, Spring MockMvc, JaCoCo coverage |
| CI/CD | Jenkins declarative pipeline (`Jenkinsfile`) |
| Container | Multi-stage `Dockerfile` (Maven/JDK build, JRE Alpine runtime) |
| Deployment | Docker Compose: 2 app replicas behind an Nginx reverse proxy |

## Project structure

```
calculator-devops/
├── src/main/java/com/spe/calculator/
│   ├── CalculatorApplication.java
│   ├── controller/CalculatorController.java   # REST endpoints
│   ├── service/CalculatorService.java         # arithmetic logic
│   ├── dto/                                   # response records
│   └── exception/                             # error handling (HTTP 400)
├── src/test/java/...                          # unit + MockMvc integration tests
├── nginx/nginx.conf                           # reverse proxy / load balancer
├── pom.xml
├── Dockerfile
├── .dockerignore
├── docker-compose.yml
├── Jenkinsfile
└── docs/report.pdf                            # project report
```

## API

Base path: `/api/v1`. Both `a` and `b` are required numbers.

| Operation | Endpoint | Result |
|-----------|----------|--------|
| Add | `GET /api/v1/add?a=&b=` | `a + b` |
| Subtract | `GET /api/v1/subtract?a=&b=` | `a - b` |
| Multiply | `GET /api/v1/multiply?a=&b=` | `a * b` |
| Divide | `GET /api/v1/divide?a=&b=` | `a / b` (`b = 0` gives HTTP 400) |
| Health | `GET /actuator/health` | `UP` / `DOWN` |

Success response:

```bash
$ curl "http://localhost:8090/api/v1/add?a=2&b=3"
{"operation":"add","a":2.0,"b":3.0,"result":5.0}
```

Error response (HTTP 400), returned for division by zero, a missing parameter, or a non-numeric value:

```bash
$ curl "http://localhost:8090/api/v1/divide?a=1&b=0"
{"timestamp":"2026-10-30T10:00:00Z","status":400,"error":"Bad Request","message":"Division by zero is not allowed","path":"/api/v1/divide"}

$ curl "http://localhost:8090/api/v1/add?a=1"
{... "status":400, "message":"Missing required parameter 'b'" ...}

$ curl "http://localhost:8090/api/v1/multiply?a=abc&b=2"
{... "status":400, "message":"Parameter 'a' must be a number, got 'abc'" ...}
```

## Build and test

Needs Java 21. Maven is downloaded by the wrapper.

```bash
./mvnw clean package -DskipTests   # build the jar -> target/calculator.jar
./mvnw test                        # run tests (reports in target/surefire-reports)
java -jar target/calculator.jar    # run locally on http://localhost:8080
```

The coverage report is written to `target/site/jacoco/index.html`.

## Run with Docker

```bash
docker build -t ashokch11/calculator-devops:latest .
docker run -d -p 8085:8080 --name calculator ashokch11/calculator-devops:latest
curl "http://localhost:8085/api/v1/multiply?a=6&b=7"
```

Dockerfile best practices used:
- multi-stage build: only the jar goes into the final image
- `pom.xml` is copied before `src` so the dependency layer is cached
- slim `eclipse-temurin:21-jre-alpine` base image
- runs as non-root user `spring`
- explicit `EXPOSE 8080` and a `HEALTHCHECK`
- `.dockerignore` keeps the build context small

## Deploy with Docker Compose

```bash
docker compose up -d          # uses ashokch11/calculator-devops:latest
docker compose ps             # all 3 containers should be "healthy"
curl -i "http://localhost:8090/api/v1/subtract?a=10&b=4"
docker compose down
```

The stack contains:
- `calculator-1`, `calculator-2`: two replicas of the API, each with a health check on `/actuator/health` and `restart: unless-stopped`
- `nginx`: reverse proxy on host port **8090** that load-balances (round robin) across the replicas. It only starts once both replicas are healthy.
- network `calc-net` for the three containers

The `X-Upstream` response header shows which replica served the request. Calling
`/actuator/info` several times also shows the instance name changing.

Environment variables:

| Variable | Default | Meaning |
|----------|---------|---------|
| `IMAGE_NAME` | `ashokch11/calculator-devops` | image repository |
| `IMAGE_TAG` | `latest` | image tag (Jenkins sets the build number) |
| `PROXY_PORT` | `8090` | host port for nginx |

## Jenkins pipeline

`Jenkinsfile` is a declarative pipeline with these stages:

| Stage | What it does |
|-------|--------------|
| Checkout | pulls the latest commit from GitHub |
| Build | `./mvnw clean package -DskipTests` |
| Test | `./mvnw test`, publishes JUnit results; the build fails if a test fails |
| Docker Build | builds the image tagged with `BUILD_NUMBER` and `latest` |
| Push | logs in with the Jenkins credential `dockerhub-creds` and pushes both tags |
| Deploy | `docker compose up -d` with the new tag, then polls `/actuator/health` until `UP` and runs a smoke test |

The pipeline polls GitHub every 2 minutes (`pollSCM`), so a `git push` starts a build with no
manual steps. Jenkins runs on localhost, so GitHub cannot reach it with a webhook. The post
section emails the result on success and failure.

Re-running the pipeline is safe: the Compose project name is fixed, and `docker compose up -d`
only recreates containers whose image changed. A running deployment is never taken down first.

### One-time Jenkins setup

1. **Plugins**: Pipeline, Git, GitHub, Credentials Binding, JUnit, Email Extension, Timestamper.
2. **Docker access**: the `jenkins` user must be in the `docker` group
   (`sudo usermod -aG docker jenkins`, then restart Jenkins).
3. **Docker Hub credential**: *Manage Jenkins → Credentials → System → Global → Add Credentials*
   - Kind: Username with password
   - Username: Docker Hub username; Password: a Docker Hub access token
   - ID: `dockerhub-creds`
4. **Email (Gmail SMTP)**: *Manage Jenkins → System → Extended E-mail Notification*
   - SMTP server `smtp.gmail.com`, port `465`, tick *Use SSL*
   - Credentials: Gmail address + a Gmail **App Password**
   - Set the same values in the *E-mail Notification* section and use *Test configuration* to check them
5. **Job**: *New Item → Pipeline* named `calculator-devops`
   - Definition: *Pipeline script from SCM* → Git →
     `https://github.com/ashokCh-dev/calculator-devops.git`, branch `*/main`
   - Script path: `Jenkinsfile`
   - Click *Build Now* once so Jenkins registers the `pollSCM` trigger.

No secrets are stored in this repository. Docker Hub and SMTP credentials live only in the
Jenkins credentials store.

## Demo flow

1. Change something (for example a message in `GlobalExceptionHandler`) and `git push`.
2. Within about 2 minutes Jenkins picks up the commit and runs all stages.
3. The new image `ashokch11/calculator-devops:<build>` appears on Docker Hub.
4. `docker ps` shows the containers recreated with the new tag, and the API on
   `http://localhost:8090` serves the change.
5. The result email arrives.
