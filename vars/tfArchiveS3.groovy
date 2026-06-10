// vars/tfArchiveS3.groovy
//
// Upload Terraform artifacts to S3 under a datalake-style prefix:
//   s3://<bucket>/<prefix>/{tf/, plan.txt, outputs.json, tfvars.redacted}
//
// Inputs:
//   workingDir : Terraform root module dir (e.g. environments/uat)
//   bucket     : S3 bucket name
//   prefix     : datalake/<env>/<repo>/<build>

def call(Map cfg) {
  def workingDir = cfg.workingDir ?: error('workingDir required')
  def bucket     = cfg.bucket     ?: error('bucket required')
  def prefix     = cfg.prefix     ?: error('prefix required')

  dir(workingDir) {
    // Redact tfvars before upload (drop lines that look like secrets).
    sh '''
      if [ -f terraform.tfvars ]; then
        sed -E 's/(secret|password|token|key)[[:space:]]*=.*/\\1 = "***REDACTED***"/Ig' \
            terraform.tfvars > tfvars.redacted
      fi
    '''

    // Upload .tf source for traceability (small, no secrets).
    sh "aws s3 cp . s3://${bucket}/${prefix}/tf/ " +
       "--recursive --exclude '*' --include '*.tf' --include '*.tfvars.example'"

    // Upload run artifacts.
    sh """
      for f in plan.txt outputs.json tfvars.redacted tfplan; do
        if [ -f "\$f" ]; then
          aws s3 cp "\$f" s3://${bucket}/${prefix}/\$f
        fi
      done
    """

    // Backup state file pulled from remote (read-only snapshot for audit).
    sh """
      terraform state pull > terraform.tfstate.snapshot || true
      if [ -s terraform.tfstate.snapshot ]; then
        aws s3 cp terraform.tfstate.snapshot \
          s3://${bucket}/${prefix}/terraform.tfstate.snapshot \
          --sse AES256
      fi
    """

    echo "Archived to s3://${bucket}/${prefix}/"
  }
}
