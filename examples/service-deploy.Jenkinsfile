@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
    // Leave blank for normal deploy (build a new image).
    // Set to a previously deployed tag (e.g. "prod-42-abc1234") to redeploy
    // that image from ECR (rollback) without rebuilding.
    string(name: 'IMAGE_TAG_OVERRIDE', defaultValue: '',
           description: 'Rollback: existing ECR tag to redeploy. Blank = normal build.'),
  ])
])

serviceDeployPipeline(
  environment:  params.ENVIRONMENT,
  service:      'ekyc',
  ecrRepo:      '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/ekyc',
  awsRegion:    'ap-southeast-1',
  clusterName:  "eks-${params.ENVIRONMENT}",
  k8sNamespace: 'ekyc',
  manifestsDir: 'k8s',
)
