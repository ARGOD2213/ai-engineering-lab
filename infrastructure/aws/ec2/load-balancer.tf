# load-balancer.tf - public ALB in 2 AZs. TLS terminates here; the ALB forwards plain
# HTTP to the API inside the VPC. local.lb_target_type is "instance" (EC2 stack)
# or "ip" (Fargate stack: every task has its own ENI/IP in awsvpc mode).

resource "aws_lb" "main" {
  name                       = "${local.name}-alb"
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [aws_security_group.alb.id]
  subnets                    = aws_subnet.public[*].id
  drop_invalid_header_fields = true
  idle_timeout               = 60
  enable_deletion_protection = true # set false (and apply) before terraform destroy
}

resource "aws_lb_target_group" "api" {
  name                 = "${local.name}-api-tg"
  port                 = var.api_port
  protocol             = "HTTP"
  vpc_id               = aws_vpc.main.id
  target_type          = local.lb_target_type
  deregistration_delay = 30 # seconds to drain in-flight requests on deploy/scale-in

  health_check {
    path                = var.health_check_path
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}
