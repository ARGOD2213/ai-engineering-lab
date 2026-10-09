#!/usr/bin/env bash
# deploy-release.sh - release (or roll back) the API on the EC2 Auto Scaling group.
# Used by GitHub Actions and by operators. Requires AWS credentials in the environment.
#
#   Deploy a new build : scripts/deploy-release.sh <release-id> path/to/app.jar
#   Roll back          : scripts/deploy-release.sh <previous-release-id>
#                        (jar already in S3, so no upload)
set -euo pipefail

release="${1:?usage: deploy-release.sh <release-id> [app.jar]}"
jar="${2:-}"

: "${AWS_REGION:?set AWS_REGION}"
: "${ARTIFACT_BUCKET:?set ARTIFACT_BUCKET}"   # ai-lab-prod-artifacts-<account-id>
: "${ASG_NAME:?set ASG_NAME}"                 # ai-lab-prod-api-asg
: "${RELEASE_PARAM:?set RELEASE_PARAM}"       # /ai-lab/prod/api/release

key="api/${release}/app.jar"

if [[ -n "$jar" ]]; then
  echo "Uploading $jar to s3://$ARTIFACT_BUCKET/$key"
  aws s3 cp "$jar" "s3://$ARTIFACT_BUCKET/$key" --region "$AWS_REGION"
else
  echo "Checking that s3://$ARTIFACT_BUCKET/$key exists"
  aws s3api head-object --bucket "$ARTIFACT_BUCKET" --key "$key" --region "$AWS_REGION" >/dev/null
fi

previous=$(aws ssm get-parameter --name "$RELEASE_PARAM" --region "$AWS_REGION" \
  --query Parameter.Value --output text)
echo "Release pointer: $previous -> $release"
aws ssm put-parameter --name "$RELEASE_PARAM" --value "$release" --type String \
  --overwrite --region "$AWS_REGION" >/dev/null

refresh_id=$(aws autoscaling start-instance-refresh \
  --auto-scaling-group-name "$ASG_NAME" \
  --strategy Rolling \
  --preferences '{"MinHealthyPercentage":100,"MaxHealthyPercentage":200,"InstanceWarmup":300}' \
  --region "$AWS_REGION" --query InstanceRefreshId --output text)
echo "Instance refresh $refresh_id started"

while true; do
  read -r status pct < <(aws autoscaling describe-instance-refreshes \
    --auto-scaling-group-name "$ASG_NAME" --instance-refresh-ids "$refresh_id" \
    --region "$AWS_REGION" \
    --query 'InstanceRefreshes[0].[Status,PercentageComplete]' --output text)
  echo "$(date -u +%H:%M:%S) status=$status complete=${pct}%"
  case "$status" in
    Successful) echo "Release $release is live"; exit 0 ;;
    Failed|Cancelled|RollbackSuccessful|RollbackFailed)
      echo "Refresh ended with $status. Restoring pointer to $previous" >&2
      aws ssm put-parameter --name "$RELEASE_PARAM" --value "$previous" --type String \
        --overwrite --region "$AWS_REGION" >/dev/null
      exit 1 ;;
  esac
  sleep 30
done
