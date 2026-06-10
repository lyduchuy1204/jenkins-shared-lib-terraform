@Library('shared-lib') _

properties([
  parameters([
    choice(name: 'ENVIRONMENT', choices: ['uat', 'staging', 'prod']),
  ])
])

eksDeployPipeline(
  environment:  params.ENVIRONMENT,
  service:      'ekyc',
  ecrRepo:      '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/ekyc',
  awsRegion:    'ap-southeast-1',
  clusterName:  "eks-${params.ENVIRONMENT}",
  k8sNamespace: 'ekyc',
  manifestsDir: 'k8s',
)
