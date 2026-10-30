---
geometry: margin=1.5cm
fontsize: 10pt
colorlinks: true
header-includes:
  - \usepackage{float}
  - \floatplacement{figure}{H}
  - \usepackage[font=small,skip=3pt]{caption}
  - \usepackage{titlesec}
  - \titlespacing*{\section}{0pt}{8pt}{3pt}
  - \pagestyle{empty}
---

\begin{center}
{\Large\bfseries CI/CD Pipeline for a Calculator REST API}\\[3pt]
CSE 816 Software Production Engineering, Mini-Project\\[2pt]
Ashok Reddy (IMT2023083) \quad · \quad 30 October 2026
\end{center}

**GitHub:** <https://github.com/ashokCh-dev/calculator-devops>  
**Docker Hub:** <https://hub.docker.com/r/ashokch11/calculator-devops>

# 1. Overview

A Spring Boot 3.5 (Java 21) REST API with `GET /api/v1/{add,subtract,multiply,divide}?a=&b=` and
`/actuator/health`. Invalid input and division by zero return HTTP 400 with a JSON error. Every
push to GitHub is built, tested, containerised, pushed to Docker Hub and deployed automatically,
and Jenkins emails the result.

![CI/CD pipeline architecture](architecture.png){ width=82% }

# 2. Pipeline Stages (Jenkinsfile)

| Stage | What it does |
|--|----------|
| **Trigger** | `pollSCM('H/2 * * * *')`: starts a build when GitHub has a new commit. |
| **Checkout** | `checkout scm` gets the commit that triggered the build. |
| **Build** | `./mvnw clean package -DskipTests` (Maven wrapper, so no Maven install is needed). |
| **Test** | `./mvnw test`: 28 JUnit 5 + MockMvc tests; results published with `junit`; a failure fails the build. |
| **Docker Build** | Builds the image tagged `:<BUILD_NUMBER>` and `:latest`. |
| **Push** | Logs in to Docker Hub with the Jenkins credential `dockerhub-creds` and pushes both tags. |
| **Deploy** | `docker compose up -d`, then waits for `/actuator/health` = `UP` through Nginx and runs a smoke test. |
| **Post** | `emailext` sends a success or failure email; dangling images are pruned. |

**Dockerfile:** multi-stage (Maven/JDK build, then `eclipse-temurin:21-jre-alpine` runtime), dependency
layer caching, non-root user, `EXPOSE 8080`, `HEALTHCHECK`, `.dockerignore`.
**Compose:** two API replicas behind an Nginx reverse proxy (round robin, port 8090) on network
`calc-net`, with health checks, `restart: unless-stopped` and environment variables. Re-runs are
safe: `up -d` only recreates changed containers.
**Git:** progressive commits, feature branches `feature/docker-compose` and
`feature/jenkins-pipeline` merged through pull requests, Java `.gitignore`, no secrets in the repo.

# 3. Challenges Faced

1. **No webhook:** Jenkins runs on localhost, so GitHub cannot reach it. I used Poll SCM every 2 minutes.
2. **Port clash:** Jenkins uses 8080, so only Nginx is published, on port 8090.
3. **Load balancing:** every request hit one replica because each Nginx worker kept its own
   round-robin state. A shared `zone` in the upstream block fixed it.
4. **Maven setup:** the `jenkins` user had no Maven. The committed Maven wrapper fixed this.
5. **Health checks:** the Alpine image has no `curl`, so the health checks use `wget`.
6. **Email:** the notification address did not match the Gmail account used for SMTP; I fixed the
   address in the Jenkinsfile.

\newpage

# 4. Results

All stages pass on every push (builds #3 and #4 were started automatically by an SCM change). The
image is on Docker Hub and the stack is healthy.

![Jenkins stage view: all stages green](report-images/jenkins-pipeline.png){ width=100% }

\begin{figure}[H]
\centering
\begin{minipage}[c]{0.40\textwidth}
\centering
\includegraphics[width=\linewidth]{report-images/email-notification.png}
\caption{Build status email}
\end{minipage}\hfill
\begin{minipage}[c]{0.57\textwidth}
\centering
\includegraphics[width=\linewidth]{report-images/docker-ps.png}
\caption{\texttt{docker ps}: replicas and proxy healthy}
\end{minipage}
\end{figure}

![Sample API responses through Nginx (port 8090)](report-images/api-responses.png){ width=100% }
