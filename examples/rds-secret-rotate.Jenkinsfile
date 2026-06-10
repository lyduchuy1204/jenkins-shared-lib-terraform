@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
  ])
])

rdsSecretRotatePipeline(
  environment: params.ENVIRONMENT,
  secretId:    "rds/ekyc/${params.ENVIRONMENT}",
  awsRegion:   'ap-southeast-1',
  healthUrl:   "https://${params.ENVIRONMENT}.ekyc.internal.example.com/healthz",
)
