# jenkins-shared-lib-terraform

Minimal Jenkins **shared library** + sample `Jenkinsfile` to drive Terraform
deploys per environment, with archived artifacts on S3.

## Design at a glance

- **Per-env agent**: each stage runs on a host labeled `tf-<env>` (`tf-uat`,
  `tf-staging`, `tf-prod`). Hosts are isolated by env (creds, network, IAM).
- **Shared library**: pipeline logic lives in `vars/terraformPipeline.groovy`
  so consumer repos only carry a 5-line `Jenkinsfile`.
- **S3 archive (datalake layout)**: after `plan`/`apply`, key files
  (`*.tf`, `terraform.tfvars` redacted, `tfplan`, `terraform.tfstate.backup`,
  `outputs.json`) are uploaded to:
  ```
  s3://<bucket>/datalake/<env>/<repo>/<build>/...
  ```

## Repo layout

```
jenkins-shared-lib-terraform/
├── Jenkinsfile                       # demo pipeline (uses the lib below)
├── vars/
│   ├── terraformPipeline.groovy      # entry: terraformPipeline(env: 'uat')
│   └── tfArchiveS3.groovy            # helper: archive artifacts to S3
└── README.md
```

## Use from another repo

In Jenkins **Manage Jenkins → System → Global Pipeline Libraries**, register
this repo as `terraform-shared-lib` (default version: `main`).

Consumer repo's `Jenkinsfile`:

```groovy
@Library('terraform-shared-lib') _

terraformPipeline(
  env:        'uat',
  workingDir: 'environments/uat',
  s3Bucket:   'my-cicd-artifacts',
)
```

## Required Jenkins setup

- Agents labeled `tf-uat`, `tf-staging`, `tf-prod`.
- Each agent has `terraform`, `aws` CLI, and an IAM role with:
  - read/write on the Terraform state bucket
  - write on `s3://<bucket>/datalake/<env>/*`
- Credentials id `aws-<env>` (optional; agents typically use instance role).
