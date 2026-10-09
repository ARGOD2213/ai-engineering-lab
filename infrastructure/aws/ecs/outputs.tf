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

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.api.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.api.family
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
