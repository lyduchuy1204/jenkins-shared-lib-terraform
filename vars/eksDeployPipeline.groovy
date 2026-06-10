// vars/eksDeployPipeline.groovy
//
// Public pipeline: clone → build → push ECR → deploy to EKS → wait rollout.
//
//   @Library('shared-lib') _
//   eksDeployPipeline(
//     environment:  params.ENVIRONMENT,         // uat | staging | prod
//     service:      'ekyc',
//     ecrRepo:      '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/ekyc',
//     awsRegion:    'ap-southeast-1',
//     clusterName:  'eks-uat',
//     k8sNamespace: 'ekyc',
//     manifestsDir: 'k8s',
//   )

def call(Map cfg) {
  def environment  = cfg.environment  ?: error('environment is required')
  def service      = cfg.service      ?: error('service is required')
  def ecrRepo      = cfg.ecrRepo      ?: error('ecrRepo is required')
  def awsRegion    = cfg.awsRegion    ?: 'ap-southeast-1'
  def clusterName  = cfg.clusterName  ?: "eks-${environment}"
  def k8sNamespace = cfg.k8sNamespace ?: service
  def manifestsDir = cfg.manifestsDir ?: 'k8s'

  pipeline {
    agent { label "eks-${environment}" }

    options { timeout(time: 30, unit: 'MINUTES') }

    environment {
      AWS_REGION = "${awsRegion}"
      ECR_REPO   = "${ecrRepo}"
      IMAGE_TAG  = "${environment}-${BUILD_NUMBER}-${GIT_COMMIT?.take(7) ?: 'dev'}"
      IMAGE      = "${ecrRepo}:${environment}-${BUILD_NUMBER}-${GIT_COMMIT?.take(7) ?: 'dev'}"
    }

    stages {
      stage('Checkout') {
        steps { checkout scm }
      }

      stage('Build') {
        steps {
          sh 'docker build -t $IMAGE .'
        }
      }

      stage('Push to ECR') {
        steps {
          sh '''
            aws ecr get-login-password --region $AWS_REGION \
              | docker login --username AWS --password-stdin ${ECR_REPO%/*}
            docker push $IMAGE
          '''
        }
      }

      stage('Deploy to EKS') {
        steps {
          sh """
            aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}
            kubectl -n ${k8sNamespace} apply -f ${manifestsDir}/
            kubectl -n ${k8sNamespace} set image \
              deployment/${service} ${service}=\$IMAGE --record
          """
        }
      }

      stage('Wait Rollout') {
        steps {
          sh "kubectl -n ${k8sNamespace} rollout status deployment/${service} --timeout=5m"
        }
      }
    }

    post {
      failure {
        sh "kubectl -n ${k8sNamespace} rollout undo deployment/${service} || true"
      }
    }
  }
}
