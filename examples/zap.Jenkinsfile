@Library('shared-lib') _

properties([
  parameters([
    string(name: 'TARGET_URL', defaultValue: 'https://uat.api.example.com'),
  ])
])

zapPipeline(
  targetUrl:  params.TARGET_URL,
  failOnHigh: true,
)
