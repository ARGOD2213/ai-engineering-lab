# rds.tf - private, encrypted, Multi-AZ PostgreSQL 17. pgvector is available on
# RDS for PostgreSQL; the master user may run CREATE EXTENSION vector.
# manage_master_user_password = true -> RDS generates the password and stores it in
# Secrets Manager. The password is never written to Terraform state or to Git.

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = aws_subnet.db[*].id
  tags       = { Name = "${local.name}-db-subnets" }
}

resource "aws_db_parameter_group" "main" {
  name   = "${local.name}-pg17"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl" # reject unencrypted client connections
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement" # log statements slower than 1 s
    value = "1000"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage # storage autoscaling ceiling
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period    = 7
  backup_window              = "03:00-04:00"
  maintenance_window         = "sun:04:30-sun:05:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  enabled_cloudwatch_logs_exports = ["postgresql"]

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-db-final"
}

locals {
  # Secret JSON: {"username": "...", "password": "..."}
  db_secret_arn = aws_db_instance.main.master_user_secret[0].secret_arn
}

# Application secrets (API keys etc.) are created ONCE with the AWS CLI before the first
# apply, so their values never pass through Terraform state. Terraform only looks it up.
data "aws_secretsmanager_secret" "app" {
  name = "${var.project}/${var.environment}/api" # ai-lab/prod/api
}
