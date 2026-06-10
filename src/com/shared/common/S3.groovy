// src/com/shared/common/S3.groovy
//
// S3 upload helper. Internal-only (not exposed as a global step).

package com.shared.common

class S3 implements Serializable {
  def steps
  S3(steps) { this.steps = steps }

  /** Upload a single file if it exists. */
  def putIfExists(String localPath, String s3Uri) {
    steps.sh "[ -f '${localPath}' ] && aws s3 cp '${localPath}' '${s3Uri}' || true"
  }

  /** Upload all files matching a glob (relative to dir). */
  def putGlob(String dir, String include, String s3Prefix) {
    steps.dir(dir) {
      steps.sh "aws s3 cp . '${s3Prefix}' --recursive --exclude '*' --include '${include}'"
    }
  }
}
