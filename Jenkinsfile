// Demo consumer pipeline.
// In a real consumer repo (e.g. iac-terraform-showcase-aws), the Jenkinsfile
// would be these 10 lines and nothing else.

@Library('terraform-shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod'],
           description: 'Target environment'),
    booleanParam(name: 'APPLY', defaultValue: false,
                 description: 'Apply after plan'),
  ])
])

terraformPipeline(
  env:        params.ENVIRONMENT,
  workingDir: "environments/${params.ENVIRONMENT}",
  s3Bucket:   'my-cicd-artifacts',
  repoName:   'iac-terraform-showcase-aws',
)
