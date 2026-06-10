# jenkins-shared-lib

Reusable Jenkins shared library for IaC, SAST, DAST, and service deploys.

## Pipelines (`vars/` — public API)

| Pipeline                | Purpose                                              | Agent label        |
| ----------------------- | ---------------------------------------------------- | ------------------ |
| `terraformPipeline`     | Run one Terraform stack of one env, archive to S3    | `tf-<env>`         |
| `sonarqubePipeline`     | SAST scan + SonarQube Quality Gate                   | `sast`             |
| `zapPipeline`           | DAST scan with OWASP ZAP baseline                    | `dast`             |
| `serviceDeployPipeline` | Build → push ECR → deploy to EKS → wait rollout      | `eks-<env>`        |

`terraformPipeline` deploys **one stack at a time** (e.g. `network` or `api`).
Use a separate Jenkins job per (env, stack) pair so each has independent
state, history, and approvals.

## Layout

```
.
├── vars/                                # public pipelines (call from Jenkinsfile)
│   ├── terraformPipeline.groovy
│   ├── sonarqubePipeline.groovy
│   ├── zapPipeline.groovy
│   └── serviceDeployPipeline.groovy
├── src/com/shared/                      # internal helpers (not exposed)
│   ├── common/S3.groovy
│   └── tf/Archiver.groovy
└── examples/                            # copy these into consumer repos
    ├── terraform-network.Jenkinsfile    # Terraform: network stack
    ├── terraform-api.Jenkinsfile        # Terraform: api stack
    ├── sonarqube.Jenkinsfile
    ├── zap.Jenkinsfile
    └── service-deploy.Jenkinsfile       # build → push ECR → deploy EKS
```

## Register in Jenkins

**Manage Jenkins → System → Global Pipeline Libraries**

- Name: `shared-lib`
- Default version: `main`
- Project repo: this repo's URL

## Usage from a consumer repo

Copy the matching example from `examples/` into your repo as `Jenkinsfile`.
For the Terraform repo `iac-terraform-showcase-aws`, create **two Jenkins
jobs**:

- One using `terraform-network.Jenkinsfile` — deploy the network stack.
- One using `terraform-api.Jenkinsfile` — deploy the api stack
  (run **after** network on the same env).

```groovy
@Library('shared-lib') _

terraformPipeline(
  environment: params.ENVIRONMENT,
  stack:       'api',
  s3Bucket:    'my-cicd-artifacts',
  repoName:    'iac-terraform-showcase-aws',
)
```

## Agent requirements

| Label         | Tools needed                                              |
| ------------- | --------------------------------------------------------- |
| `tf-<env>`    | `terraform`, `aws` CLI, IAM access to state + datalake S3 |
| `sast`        | `sonar-scanner`, SonarQube server reachable               |
| `dast`        | `docker` (runs `ghcr.io/zaproxy/zaproxy:stable`)          |
| `eks-<env>`   | `docker`, `aws` CLI, `kubectl`, ECR push + EKS access     |

## Conventions

- **`vars/*Pipeline.groovy`** — public pipelines. Anything else placed in
  `vars/` would also become a global step; keep it small and intentional.
- **`src/com/shared/...`** — Groovy classes used internally by pipelines.
  Not part of the public API.
- **`examples/*.Jenkinsfile`** — reference snippets to drop into consumer
  repos. Not executed by this repo.
