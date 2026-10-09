# ecs-cluster.tf - the logical cluster and the log group the containers write to.
# With Fargate there are no instances to register: the cluster is just a namespace.

resource "aws_ecs_cluster" "main" {
  name = local.name # ai-lab-prod

  setting {
    name  = "containerInsights"
    value = "enabled" # per-task CPU/memory metrics (extra CloudWatch cost)
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}-api"
  retention_in_days = 30
}
