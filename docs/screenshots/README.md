# Screenshots

Screenshots required for the submission:

1. `jenkins-pipeline.png`: green Jenkins pipeline run (stage view with all stages passing)
2. `email-notification.png`: the build status email
3. `docker-ps.png`: `docker ps` showing calculator-1, calculator-2 and calculator-nginx as healthy
4. `api-responses.png`: sample API calls, for example:

```bash
curl "http://localhost:8090/api/v1/add?a=2&b=3"
curl "http://localhost:8090/api/v1/divide?a=1&b=0"
curl -i "http://localhost:8090/api/v1/multiply?a=6&b=7"
curl "http://localhost:8090/actuator/health"
```
