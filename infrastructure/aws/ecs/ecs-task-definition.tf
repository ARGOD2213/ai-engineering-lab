# ecs-task-definition.tf - WHAT to run: image, CPU/memory, ports, env, secrets, health
# check, logs. Each change creates a new immutable REVISION (ai-lab-prod-api:1, :2, ...).
# Terraform owns the SHAPE; CI only swaps the image and registers new revisions.

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64" # use ARM64 only if the image is built for arm64
  }

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = "${aws_ecr_repository.api.repository_url}:${var.initial_image_tag}"
      essential = true

      portMappings = [{ containerPort = var.api_port, protocol = "tcp" }]

      # Plain, non-sensitive configuration.
      environment = [
        { name = "SERVER_PORT", value = tostring(var.api_port) },
        { name = "POSTGRES_HOST", value = aws_db_instance.main.address },
        { name = "POSTGRES_PORT", value = "5432" },
        { name = "POSTGRES_DB", value = var.db_name },
      ]

      # Resolved by the ECS agent at task start using the EXECUTION role.
      # Format: <secret-arn>:<json-key>:<version-stage>:<version-id>
      secrets = [
        { name = "POSTGRES_USER", valueFrom = "${local.db_secret_arn}:username::" },
        { name = "POSTGRES_PASSWORD", valueFrom = "${local.db_secret_arn}:password::" },
        { name = "OPENAI_API_KEY", valueFrom = "${data.aws_secretsmanager_secret.app.arn}:OPENAI_API_KEY::" },
      ]

      # Container-level health check (runs inside the container; image needs curl).
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:${var.api_port}${var.health_check_path} || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 120 # JVM start-up grace; failures are ignored during this window
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "api"
        }
      }

      stopTimeout = 30 # seconds between SIGTERM and SIGKILL (graceful shutdown)
    }
  ])
}
