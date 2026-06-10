// src/com/portfolio/tf/Archiver.groovy
//
// Archives Terraform run artifacts to S3 (datalake layout).
// Internal helper used by terraformPipeline.

package com.portfolio.tf

import com.portfolio.common.S3

class Archiver implements Serializable {
  def steps
  Archiver(steps) { this.steps = steps }

  def archive(String workingDir, String bucket, String prefix) {
    def base = "s3://${bucket}/${prefix}"
    def s3   = new S3(steps)
    s3.putGlob(workingDir, '*.tf', "${base}/tf/")
    steps.dir(workingDir) {
      s3.putIfExists('plan.txt',    "${base}/plan.txt")
      s3.putIfExists('outputs.json', "${base}/outputs.json")
    }
    steps.echo "Archived to ${base}/"
  }
}
