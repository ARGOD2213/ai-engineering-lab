# ec2.tf - artifact bucket, release pointer, launch template, Auto Scaling group,
# scaling policies and the instance log group.
#
# Release model (immutable instances):
#   CI uploads s3://<artifacts>/api/<sha>/app.jar -> sets SSM /ai-lab/prod/api/release = <sha>
#   -> starts an ASG instance refresh -> each NEW instance runs user-data, reads the
#   parameter, downloads that jar, starts systemd. Old instances are terminated after the
#   new ones pass ALB health checks. Roll back = point the parameter at the old SHA + refresh.

locals {
  lb_target_type = "instance"
}

# ---- Artifacts ------------------------------------------------------------------------
resource "aws_s3_bucket" "artifacts" {
  bucket = "${local.name}-artifacts-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    id     = "expire-old-releases"
    status = "Enabled"
    filter {
      prefix = "api/"
    }
    expiration {
      days = 90 # keep a 90-day rollback window
    }
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

# Pointer to the release that NEW instances install. Terraform creates it once; CI owns
# the value afterwards (ignore_changes stops Terraform from reverting deployments).
resource "aws_ssm_parameter" "api_release" {
  name  = "/${var.project}/${var.environment}/api/release" # /ai-lab/prod/api/release
  type  = "String"
  value = var.initial_release
  lifecycle {
    ignore_changes = [value]
  }
}

# ---- Logs -------------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ec2/${local.name}-api"
  retention_in_days = 30
}

# ---- Launch template ------------------------------------------------------------------
# Latest Amazon Linux 2023 AMI (published by AWS as a public SSM parameter). A new AMI
# appears in the plan as a launch-template change -> Terraform triggers an instance refresh.
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_launch_template" "api" {
  name_prefix            = "${local.name}-api-"
  image_id               = data.aws_ssm_parameter.al2023_ami.insecure_value
  instance_type          = var.instance_type
  vpc_security_group_ids = [aws_security_group.app.id]
  update_default_version = true

  iam_instance_profile {
    arn = aws_iam_instance_profile.api.arn
  }

  # IMDSv2 only; hop limit 1 keeps instance credentials away from containers/proxies.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 20
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  monitoring {
    enabled = true # 1-minute EC2 metrics
  }

  user_data = base64encode(templatefile("${path.module}/templates/user-data.sh.tftpl", {
    aws_region      = var.aws_region
    artifact_bucket = aws_s3_bucket.artifacts.bucket
    release_param   = aws_ssm_parameter.api_release.name
    db_secret_arn   = local.db_secret_arn
    app_secret_arn  = data.aws_secretsmanager_secret.app.arn
    db_host         = aws_db_instance.main.address
    db_name         = var.db_name
    api_port        = var.api_port
    log_group       = aws_cloudwatch_log_group.api.name
    systemd_unit    = file("${path.module}/templates/ai-lab-api.service")
  }))

  tag_specifications {
    resource_type = "instance"
    tags          = { App = "${local.name}-api" }
  }
}

# ---- Auto Scaling group -------------------------------------------------------------
resource "aws_autoscaling_group" "api" {
  name                = "${local.name}-api-asg"
  vpc_zone_identifier = aws_subnet.private[*].id # spread across AZs
  min_size            = var.asg_min_size
  max_size            = var.asg_max_size
  desired_capacity    = var.asg_desired_capacity
  target_group_arns   = [aws_lb_target_group.api.arn]

  health_check_type         = "ELB" # replace instances the ALB marks unhealthy
  health_check_grace_period = 300   # boot + package install + JVM start
  default_instance_warmup   = 300

  launch_template {
    id      = aws_launch_template.api.id
    version = aws_launch_template.api.latest_version
  }

  # Terraform starts this refresh automatically when the launch template changes.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 100 # launch new before terminating old
      max_healthy_percentage = 200
      instance_warmup        = 300
      auto_rollback          = true # revert to the previous launch template on failure
    }
  }

  enabled_metrics = ["GroupDesiredCapacity", "GroupInServiceInstances", "GroupTotalInstances"]

  tag {
    key                 = "Name"
    value               = "${local.name}-api"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity] # scaling policies own it after creation
  }
}

# ---- Scaling policies (ILLUSTRATIVE targets) ------------------------------------------
resource "aws_autoscaling_policy" "api_cpu" {
  name                   = "${local.name}-api-cpu"
  autoscaling_group_name = aws_autoscaling_group.api.name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 50
  }
}

resource "aws_autoscaling_policy" "api_requests" {
  name                   = "${local.name}-api-requests"
  autoscaling_group_name = aws_autoscaling_group.api.name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.main.arn_suffix}/${aws_lb_target_group.api.arn_suffix}"
    }
    target_value = 1000 # requests per target per minute - REPLACE with a load-tested value
  }
}
