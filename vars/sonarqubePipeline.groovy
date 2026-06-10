// vars/sonarqubePipeline.groovy
//
// Public pipeline: SAST scan with SonarQube + Quality Gate.
//
//   @Library('shared-lib') _
//   sonarqubePipeline(
//     projectKey: 'ekyc-service',
//     sonarEnv:   'sonarqube-prod',   // configured in Manage Jenkins -> SonarQube
//     agentLabel: 'sast',
//   )

def call(Map cfg) {
  def projectKey = cfg.projectKey ?: error('projectKey is required')
  def sonarEnv   = cfg.sonarEnv   ?: 'sonarqube'
  def agentLabel = cfg.agentLabel ?: 'sast'
  def sources    = cfg.sources    ?: '.'

  pipeline {
    agent { label agentLabel }

    options { timeout(time: 20, unit: 'MINUTES') }

    stages {
      stage('Scan') {
        steps {
          withSonarQubeEnv(sonarEnv) {
            sh """
              sonar-scanner \
                -Dsonar.projectKey=${projectKey} \
                -Dsonar.sources=${sources} \
                -Dsonar.sourceEncoding=UTF-8
            """
          }
        }
      }

      stage('Quality Gate') {
        steps {
          timeout(time: 5, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
          }
        }
      }
    }
  }
}
