# versions.tf - pins Terraform + provider versions and stores state remotely.
# The state bucket is created ONCE by hand (see the bootstrap step); never commit state.

terraform {
  required_version = ">= 1.10.0" # S3 native state locking (use_lockfile)

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket       = "ai-lab-tfstate-111122223333" # REPLACE: your state bucket (account ID suffix)
    key          = "prod/ecs/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true # lock file in S3; no DynamoDB table needed
  }
}
