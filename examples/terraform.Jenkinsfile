@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
    booleanParam(name: 'APPLY', defaultValue: false),
  ])
])

terraformPipeline(
  environment: params.ENVIRONMENT,
  s3Bucket:    'my-cicd-artifacts',
  repoName:    'iac-terraform-showcase-aws',
)
