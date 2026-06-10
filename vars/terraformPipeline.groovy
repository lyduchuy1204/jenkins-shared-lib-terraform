// vars/terraformPipeline.groovy
//
// Public pipeline: Terraform plan/apply per environment with S3 archive.
//
//   @Library('shared-lib') _
//   terraformPipeline(
//     environment: params.ENVIRONMENT,   // uat | staging | prod
//     s3Bucket:    'my-cicd-artifacts',
//     repoName:    'iac-terraform-showcase-aws',
//   )

import com.shared.tf.Archiver

def call(Map cfg) {
  def environment = cfg.environment ?: error('environment is required')
  def workingDir  = cfg.workingDir  ?: "environments/${environment}"
  def s3Bucket    = cfg.s3Bucket    ?: error('s3Bucket is required')
  def repoName    = cfg.repoName    ?: 'unknown'

  pipeline {
    agent { label "tf-${environment}" }

    options { timeout(time: 30, unit: 'MINUTES') }

    environment {
      TF_IN_AUTOMATION = '1'
      TF_INPUT         = '0'
      WORKDIR          = "${workingDir}"
      S3_BUCKET        = "${s3Bucket}"
      S3_PREFIX        = "datalake/${environment}/${repoName}/${BUILD_NUMBER}"
    }

    stages {
      stage('Init & Validate') {
        steps {
          dir(env.WORKDIR) {
            sh 'terraform init -reconfigure'
            sh 'terraform fmt -check -recursive'
            sh 'terraform validate'
          }
        }
      }

      stage('Plan') {
        steps {
          dir(env.WORKDIR) {
            sh 'terraform plan -out=tfplan -no-color | tee plan.txt'
          }
        }
      }

      stage('Approve (prod)') {
        when { expression { environment == 'prod' && params.APPLY } }
        steps { input message: 'Apply to prod?', ok: 'Apply' }
      }

      stage('Apply') {
        when { expression { params.APPLY == true } }
        steps {
          dir(env.WORKDIR) {
            sh 'terraform apply -auto-approve tfplan'
            sh 'terraform output -json > outputs.json'
          }
        }
      }

      stage('Archive') {
        steps {
          script {
            new Archiver(this).archive(env.WORKDIR, env.S3_BUCKET, env.S3_PREFIX)
          }
        }
      }
    }
  }
}
