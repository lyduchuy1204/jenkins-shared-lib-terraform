// Pipeline for the NETWORK stack of iac-terraform-showcase-aws.
// One Jenkins job uses this Jenkinsfile; create another job for the api stack.

@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
    booleanParam(name: 'APPLY', defaultValue: false),
  ])
])

terraformPipeline(
  environment: params.ENVIRONMENT,
  stack:       'network',
  s3Bucket:    'my-cicd-artifacts',
  repoName:    'iac-terraform-showcase-aws',
)
