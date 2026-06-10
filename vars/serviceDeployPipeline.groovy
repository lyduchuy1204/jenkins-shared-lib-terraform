// vars/serviceDeployPipeline.groovy
//
// Public pipeline: build a service container, push to ECR, render k8s
// manifests with the new image tag, apply, and wait for rollout.
//
// Note: this pipeline deploys an APPLICATION to an existing EKS cluster.
// Provisioning the EKS cluster itself is out of scope here.
//
// Rollback: trigger the same job with parameter IMAGE_TAG_OVERRIDE set
// to a previously deployed tag (e.g. "prod-42-abc1234"). Build and Push
// stages are skipped; the existing image in ECR is redeployed.
//
//   @Library('shared-lib') _
//   serviceDeployPipeline(
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

  // If IMAGE_TAG_OVERRIDE is provided, redeploy that tag (rollback path).
  def override = (params?.IMAGE_TAG_OVERRIDE ?: '').trim()
  def isRollback = override != ''
  def tag = isRollback
              ? override
              : "${environment}-${BUILD_NUMBER}-${GIT_COMMIT?.take(7) ?: 'dev'}"

  pipeline {
    agent { label "eks-${environment}" }

    options { timeout(time: 30, unit: 'MINUTES') }

    environment {
      AWS_REGION  = "${awsRegion}"
      ECR_REPO    = "${ecrRepo}"
      IMAGE_TAG   = "${tag}"
      IMAGE       = "${ecrRepo}:${tag}"
      IS_ROLLBACK = "${isRollback}"
    }

    stages {
      stage('Checkout') {
        steps { checkout scm }
      }

      stage('Resolve image') {
        steps {
          script {
            if (isRollback) {
              echo "Rollback mode: redeploying existing image ${env.IMAGE}"
            } else {
              echo "Forward deploy: will build and push ${env.IMAGE}"
            }
          }
        }
      }

      stage('Build') {
        when { expression { !isRollback } }
        steps {
          sh 'docker build -t $IMAGE .'
        }
      }

      stage('Push to ECR') {
        when { expression { !isRollback } }
        steps {
          sh 'aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin ${ECR_REPO%/*}'
          sh 'docker push $IMAGE'
        }
      }

      stage('Verify image exists in ECR') {
        when { expression { isRollback } }
        steps {
          // Fail fast if the requested rollback tag is not in ECR.
          sh "aws ecr describe-images --region \$AWS_REGION --repository-name \$(echo \$ECR_REPO | cut -d/ -f2-) --image-ids imageTag=\$IMAGE_TAG > /dev/null"
        }
      }

      stage('Approve (prod)') {
        when { expression { environment == 'prod' } }
        steps {
          input message: "Deploy ${tag} to prod?", ok: 'Deploy'
        }
      }

      stage('Deploy to EKS') {
        steps {
          sh "aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}"
          sh "sed -i 's|${ecrRepo}:placeholder|\$IMAGE|g' ${manifestsDir}/deployment.yaml"
          sh "kubectl -n ${k8sNamespace} apply -f ${manifestsDir}/"
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
