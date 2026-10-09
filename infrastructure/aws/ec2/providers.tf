# providers.tf - which AWS region/account Terraform talks to.
# Default provider = the workload region. The aliased provider exists ONLY because
# CloudFront requires its ACM certificate to live in us-east-1.

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  name       = "${var.project}-${var.environment}"       # ai-lab-prod
  app_fqdn   = "${var.app_subdomain}.${var.domain_name}" # app.example.com
  api_fqdn   = "${var.api_subdomain}.${var.domain_name}" # api.example.com
  account_id = data.aws_caller_identity.current.account_id
}
