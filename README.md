# jenkins-shared-lib

Minimal Jenkins shared library with reusable pipelines for IaC, SAST, DAST,
and EKS deployment.

## Pipelines (`vars/` — public API)

| Pipeline             | Purpose                                        | Agent label        |
| -------------------- | ---------------------------------------------- | ------------------ |
| `terraformPipeline`  | Terraform plan/apply per env, archive to S3    | `tf-<env>`         |
| `sonarqubePipeline`  | SAST scan + SonarQube Quality Gate             | `sast`             |
| `zapPipeline`        | DAST scan with OWASP ZAP baseline              | `dast`             |
| `eksDeployPipeline`  | Build → push ECR → deploy EKS → wait rollout   | `eks-<env>`        |

## Layout

```
.
├── vars/                                # public pipelines (call from Jenkinsfile)
│   ├── terraformPipeline.groovy
│   ├── sonarqubePipeline.groovy
│   ├── zapPipeline.groovy
│   └── eksDeployPipeline.groovy
├── src/com/shared/                      # internal helpers (not exposed)
│   ├── common/S3.groovy
│   └── tf/Archiver.groovy
└── examples/                            # copy these into consumer repos
    ├── terraform.Jenkinsfile
    ├── sonarqube.Jenkinsfile
    ├── zap.Jenkinsfile
    └── service-deploy.Jenkinsfile        # build → push ECR → deploy EKS
```

## Register in Jenkins

**Manage Jenkins → System → Global Pipeline Libraries**

- Name: `shared-lib`
- Default version: `main`
- Project repo: this repo's URL

## Usage from a consumer repo

Copy the matching example from `examples/` into your repo as `Jenkinsfile`:

```groovy
@Library('shared-lib') _

terraformPipeline(
  environment: params.ENVIRONMENT,
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

- **`vars/*Pipeline.groovy`** — public pipelines (anything else here would
  also be a global step; keep it small).
- **`src/com/shared/...`** — Groovy classes used internally. Not part of
  the public API.
- **`examples/*.Jenkinsfile`** — reference snippets to drop into consumer
  repos. Not executed by this repo.
