// vars/terraformPipeline.groovy
//
// Public pipeline: Terraform plan/apply for ONE stack of ONE environment.
// Repo "iac-terraform-showcase-aws" exposes two stacks per env:
//   environments/<env>/network/
//   environments/<env>/api/
// Use one Jenkins job per (env, stack) pair so they have independent
// state, approvals, and history.
//
//   @Library('shared-lib') _
//   terraformPipeline(
//     environment: params.ENVIRONMENT,   // uat | staging | prod
//     stack:       'network',            // network | api
//     s3Bucket:    'my-cicd-artifacts',
//     repoName:    'iac-terraform-showcase-aws',
//   )

import com.shared.tf.Archiver

def call(Map cfg) {
  def environment = cfg.environment ?: error('environment is required')
  def stack       = cfg.stack       ?: error('stack is required (network|api)')
  def workingDir  = cfg.workingDir  ?: "environments/${environment}/${stack}"
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
      S3_PREFIX        = "datalake/${environment}/${repoName}/${stack}/${BUILD_NUMBER}"
    }

    stages {
      stage('Init') {
        steps {
          dir(env.WORKDIR) {
            sh 'terraform init -reconfigure'
          }
        }
      }

      stage('Validate') {
        steps {
          dir(env.WORKDIR) {
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
        steps { input message: "Apply ${stack} to prod?", ok: 'Apply' }
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
