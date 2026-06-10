// vars/zapPipeline.groovy
//
// Public pipeline: DAST scan with OWASP ZAP baseline against a deployed URL.
//
//   @Library('shared-lib') _
//   zapPipeline(
//     targetUrl:  'https://uat.api.example.com',
//     agentLabel: 'dast',
//     failOnHigh: true,
//   )

def call(Map cfg) {
  def targetUrl  = cfg.targetUrl  ?: error('targetUrl is required')
  def agentLabel = cfg.agentLabel ?: 'dast'
  def failOnHigh = cfg.failOnHigh != null ? cfg.failOnHigh : true
  // ZAP baseline exit codes: 0=clean, 1=warn, 2=fail. -I makes warns non-fatal.
  def zapFlags   = failOnHigh ? '' : '-I'

  pipeline {
    agent { label agentLabel }

    options { timeout(time: 30, unit: 'MINUTES') }

    stages {
      stage('ZAP Baseline Scan') {
        steps {
          sh """
            docker run --rm -v \$(pwd):/zap/wrk/:rw \
              ghcr.io/zaproxy/zaproxy:stable \
              zap-baseline.py -t ${targetUrl} \
              -r zap-report.html -J zap-report.json ${zapFlags}
          """
        }
      }

      stage('Publish Report') {
        steps {
          archiveArtifacts artifacts: 'zap-report.html, zap-report.json',
                           allowEmptyArchive: true
        }
      }
    }
  }
}
