# ecs-service.tf - HOW MANY copies run, WHERE (private subnets, 2 AZs), behind WHICH
# target group, and HOW deployments roll (rolling + circuit breaker + alarm rollback).

locals {
  lb_target_type = "ip" # Fargate tasks register by ENI IP
}

resource "aws_ecs_service" "api" {
  name                   = "${local.name}-api"
  cluster                = aws_ecs_cluster.main.id
  task_definition        = aws_ecs_task_definition.api.arn
  desired_count          = var.service_desired_count
  launch_type            = "FARGATE"
  platform_version       = "LATEST"
  enable_execute_command = true # OPTIONAL: ECS Exec for debugging
  propagate_tags         = "SERVICE"

  # Ignore ALB health checks for this long after a task starts (JVM warm-up).
  health_check_grace_period_seconds = 120

  # Rolling update: start new tasks first (up to 200%), never drop below 100% healthy.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_controller {
    type = "ECS"
  }

  # Roll back automatically when new tasks keep failing to start or to pass health checks.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Also roll back when this alarm fires during the deployment.
  alarms {
    enable      = true
    rollback    = true
    alarm_names = [aws_cloudwatch_metric_alarm.api_5xx.alarm_name]
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = var.api_port
  }

  depends_on = [aws_lb_listener.https]

  lifecycle {
    # task_definition: CI registers new revisions and points the service at them.
    # desired_count  : Service Auto Scaling owns it after creation.
    ignore_changes = [task_definition, desired_count]
  }
}
