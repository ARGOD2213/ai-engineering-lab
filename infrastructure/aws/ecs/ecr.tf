# ecr.tf - private registry for the API image. Tags are IMMUTABLE: tag = git SHA, so a
# tag always means exactly one build and rollbacks are deterministic.

resource "aws_ecr_repository" "api" {
  name                 = "${var.project}/api" # ai-lab/api
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 30 most recent images (rollback window)"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = { type = "expire" }
    }]
  })
}
