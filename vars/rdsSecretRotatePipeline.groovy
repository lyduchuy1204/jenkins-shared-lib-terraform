// vars/rdsSecretRotatePipeline.groovy
//
// Trigger AWS Secrets Manager rotation for an RDS secret.
// Logic (poll, smoke test, rollback) is in resources/python/rotate_rds_secret.py.
//
//   @Library('shared-lib') _
//   rdsSecretRotatePipeline(
//     environment: params.ENVIRONMENT,
//     secretId:    'rds/ekyc/uat',
//     awsRegion:   'ap-southeast-1',
//     healthUrl:   'https://uat.ekyc.internal.example.com/healthz',
//   )

def call(Map cfg) {
  def environment = cfg.environment ?: error('environment is required')
  def secretId    = cfg.secretId    ?: error('secretId is required')
  def awsRegion   = cfg.awsRegion   ?: 'ap-southeast-1'
  def healthUrl   = cfg.healthUrl   ?: error('healthUrl is required')

  pipeline {
    agent { label "secops-${environment}" }

    options {
      timeout(time: 20, unit: 'MINUTES')
      timestamps()
      disableConcurrentBuilds()
    }

    stages {
      stage('Pre-flight') {
        steps {
          sh "aws secretsmanager describe-secret --secret-id ${secretId} --region ${awsRegion} --query RotationEnabled --output text | grep -qx True"
          sh "curl -fsk -o /dev/null ${healthUrl}"
        }
      }

      stage('Approve (prod)') {
        when { expression { environment == 'prod' } }
        steps {
          input message: "Rotate ${secretId} (build #${BUILD_NUMBER})?", ok: 'Rotate'
        }
      }

      stage('Rotate') {
        steps {
          writeFile file: 'rotate.py', text: libraryResource('python/rotate_rds_secret.py')
          sh "python3 rotate.py --secret-id ${secretId} --region ${awsRegion} --health-url ${healthUrl}"
        }
      }
    }
  }
}
