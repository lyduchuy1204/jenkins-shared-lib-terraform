// vars/terraformPipeline.groovy
//
// Reusable Terraform pipeline.
// Usage from a consumer Jenkinsfile:
//
//   @Library('terraform-shared-lib') _
//   terraformPipeline(env: 'uat', workingDir: 'environments/uat',
//                     s3Bucket: 'my-cicd-artifacts')

def call(Map cfg) {
  def env        = cfg.env        ?: error('env is required (uat|staging|prod)')
  def workingDir = cfg.workingDir ?: "environments/${env}"
  def s3Bucket   = cfg.s3Bucket   ?: error('s3Bucket is required')
  def agentLabel = "tf-${env}"
  def repoName   = (cfg.repoName ?: env.JOB_NAME ?: 'unknown')
                     .toString().replaceAll('/', '_')

  pipeline {
    agent { label agentLabel }

    options {
      timestamps()
      ansiColor('xterm')
      timeout(time: 30, unit: 'MINUTES')
      disableConcurrentBuilds()
    }

    environment {
      TF_IN_AUTOMATION = '1'
      TF_INPUT         = '0'
      ENV              = "${env}"
      WORKDIR          = "${workingDir}"
      S3_BUCKET        = "${s3Bucket}"
      S3_PREFIX        = "datalake/${env}/${repoName}/${BUILD_NUMBER}"
    }

    stages {
      stage('Checkout') {
        steps { checkout scm }
      }

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

      stage('Approve') {
        when { expression { return env.ENV == 'prod' } }
        steps {
          input message: "Apply Terraform to ${env.ENV}?", ok: 'Apply'
        }
      }

      stage('Apply') {
        when { expression { return params.APPLY == true } }
        steps {
          dir(env.WORKDIR) {
            sh 'terraform apply -auto-approve tfplan'
            sh 'terraform output -json > outputs.json'
          }
        }
      }

      stage('Archive to S3') {
        steps {
          tfArchiveS3(
            workingDir: env.WORKDIR,
            bucket:     env.S3_BUCKET,
            prefix:     env.S3_PREFIX,
          )
        }
      }
    }

    post {
      always {
        archiveArtifacts artifacts: "${workingDir}/plan.txt," +
                                    "${workingDir}/outputs.json",
                         allowEmptyArchive: true
      }
      failure {
        echo "Pipeline FAILED on env=${env.ENV} build=${env.BUILD_NUMBER}"
      }
    }
  }
}
