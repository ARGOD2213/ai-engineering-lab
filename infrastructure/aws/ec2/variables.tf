# variables.tf - every input of the EC2 stack. Values go in terraform.tfvars.

variable "project" {
  description = "Short project name used as a prefix for every resource"
  type        = string
  default     = "ai-lab"
}

variable "environment" {
  description = "Environment name (prod, staging, ...)"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "Region for the VPC, ALB, compute and RDS"
  type        = string
  default     = "us-east-1"
}

variable "domain_name" {
  description = "Apex domain of an existing Route 53 public hosted zone"
  type        = string
}

variable "app_subdomain" {
  description = "Subdomain for the React app (CloudFront)"
  type        = string
  default     = "app"
}

variable "api_subdomain" {
  description = "Subdomain for the backend API (ALB)"
  type        = string
  default     = "api"
}

variable "github_repo" {
  description = "GitHub repository allowed to deploy, as OWNER/REPO"
  type        = string
}

variable "alert_email" {
  description = "Email for CloudWatch alarm notifications (empty = none)"
  type        = string
  default     = ""
}

# --- Network -----------------------------------------------------------------
variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones (2 minimum for ALB and Multi-AZ RDS)"
  type        = number
  default     = 2
  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count must be 2 or 3."
  }
}

variable "single_nat_gateway" {
  description = "true = one NAT (cheaper, AZ-level single point of failure); false = one per AZ"
  type        = bool
  default     = true
}

# --- API -----------------------------------------------------------------------
variable "api_port" {
  type    = number
  default = 8080
}

variable "health_check_path" {
  type    = string
  default = "/actuator/health"
}

# --- Database --------------------------------------------------------------------
variable "db_engine_version" {
  type    = string
  default = "17"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "db_allocated_storage" {
  type    = number
  default = 50
}

variable "db_max_allocated_storage" {
  type    = number
  default = 200
}

variable "db_multi_az" {
  type    = bool
  default = true
}

variable "db_name" {
  type    = string
  default = "ai_lab"
}

variable "db_username" {
  type    = string
  default = "ai_lab_admin"
}

variable "db_connections_alarm_threshold" {
  description = "Alarm when connections exceed this; derive from SHOW max_connections"
  type        = number
  default     = 300
}

# --- EC2 Auto Scaling (ILLUSTRATIVE sizes: confirm with load tests) --------------
variable "instance_type" {
  type    = string
  default = "t3.medium"
}

variable "asg_min_size" {
  type    = number
  default = 2
}

variable "asg_desired_capacity" {
  type    = number
  default = 2
}

variable "asg_max_size" {
  type    = number
  default = 10
}

variable "initial_release" {
  description = "Release ID (git SHA) instances deploy on first boot; CI updates the SSM parameter afterwards"
  type        = string
}
