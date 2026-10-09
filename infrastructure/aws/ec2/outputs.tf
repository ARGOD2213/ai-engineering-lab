# outputs.tf - values the CI workflow and operators need after apply.
# Read with: terraform output -raw <name>

output "app_url" {
  value = "https://${local.app_fqdn}"
}

output "api_url" {
  value = "https://${local.api_fqdn}"
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "asg_name" {
  value = aws_autoscaling_group.api.name
}

output "artifact_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "release_parameter" {
  value = aws_ssm_parameter.api_release.name
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.frontend.id
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "db_secret_arn" {
  value = local.db_secret_arn
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
