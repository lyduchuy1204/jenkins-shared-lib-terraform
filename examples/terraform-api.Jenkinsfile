// Pipeline for the API (serverless: API GW + Lambda + ALB) stack of
// iac-terraform-showcase-aws. The api stack reads outputs of the network
// stack via terraform_remote_state, so deploy network first.

@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
    booleanParam(name: 'APPLY', defaultValue: false),
  ])
])

terraformPipeline(
  environment: params.ENVIRONMENT,
  stack:       'api',
  s3Bucket:    'my-cicd-artifacts',
  repoName:    'iac-terraform-showcase-aws',
)
