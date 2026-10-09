# iam.tf - instance role (what code ON the EC2 instance may do) + CI deploy permissions.
# The instance profile is the container that attaches the role to an instance.

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "api_instance" {
  name               = "${local.name}-api-instance-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# Session Manager shell + Run Command + patching, without SSH or a bastion.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.api_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Lets the CloudWatch agent ship logs and memory/disk metrics.
resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.api_instance.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

data "aws_iam_policy_document" "api_instance" {
  statement {
    sid       = "ReadReleaseArtifacts"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/api/*"]
  }
  statement {
    sid       = "ReadReleasePointer"
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.api_release.arn]
  }
  statement {
    sid       = "ReadRuntimeSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [local.db_secret_arn, data.aws_secretsmanager_secret.app.arn]
  }
}

resource "aws_iam_role_policy" "api_instance" {
  name   = "api-runtime"
  role   = aws_iam_role.api_instance.id
  policy = data.aws_iam_policy_document.api_instance.json
}

resource "aws_iam_instance_profile" "api" {
  name = "${local.name}-api-instance-profile"
  role = aws_iam_role.api_instance.name
}

# ---- GitHub Actions: upload artifact, move release pointer, refresh instances ------
data "aws_iam_policy_document" "github_ec2" {
  statement {
    sid       = "UploadArtifacts"
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/api/*"]
  }
  statement {
    sid       = "MoveReleasePointer"
    actions   = ["ssm:GetParameter", "ssm:PutParameter"]
    resources = [aws_ssm_parameter.api_release.arn]
  }
  statement {
    sid       = "StartRefresh"
    actions   = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"]
    resources = [aws_autoscaling_group.api.arn]
  }
  statement {
    sid       = "DescribeRefresh"
    actions   = ["autoscaling:DescribeInstanceRefreshes", "autoscaling:DescribeAutoScalingGroups"]
    resources = ["*"] # Describe* actions do not support resource-level permissions
  }
}

resource "aws_iam_role_policy" "github_ec2" {
  name   = "ec2-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_ec2.json
}
