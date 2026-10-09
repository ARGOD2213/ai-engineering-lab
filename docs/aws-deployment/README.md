# AWS deployment: learning path and to-do list

How to deploy this lab (React frontend + `ai-engineering-api` + PostgreSQL/pgvector) on AWS, two ways:

- **Approach A: Amazon EC2** (VMs, Auto Scaling group, jar + systemd)
- **Approach B: Amazon ECS on Fargate** (containers, no servers to patch). **This is the recommended first production path.**

| File | What it is |
|------|------------|
| [`AWS-Deployment-Guide-EC2-vs-ECS-Fargate.pdf`](AWS-Deployment-Guide-EC2-vs-ECS-Fargate.pdf) | Main runbook (Parts 1–10): diagrams, step-by-step deployment, rollback, security, scaling, checklist |
| [`AWS-Deployment-Guide-Appendix-Complete-Files.pdf`](AWS-Deployment-Guide-Appendix-Complete-Files.pdf) | Every configuration file, in full |
| [`reference/`](reference/) | Rendered JSON (task definition, IAM policies) for learning only. Not used by any tool |

The files the PDFs describe are in the repository:

```
infrastructure/aws/ec2/                    Terraform stack for Approach A (+ templates/, scripts/)
infrastructure/aws/ecs/                    Terraform stack for Approach B
infrastructure/aws/github-workflows/       CI/CD workflows (inactive templates, see below)
services/ai-engineering-api/Dockerfile     Container image for Approach B
services/ai-engineering-api/.dockerignore
```

> **Status:** `terraform validate` and a mocked `terraform plan` pass for both stacks (Terraform 1.16.5,
> AWS provider 6.68.0). Scripts pass `shellcheck` and workflows pass `actionlint`. None of this has been applied
> to a real AWS account yet, the Docker image has not been built, and no load test has been run.

> **Workflows are parked on purpose.** If they were in `.github/workflows/`, every push to `main` would try to
> deploy and fail, because no AWS account is wired up yet. When you reach phase 5 below, move **one** of them
> into `.github/workflows/`, not both.

---

## Learning path (read in this order)

| # | Read | Goal: you can explain… |
|---|------|------------------------|
| 1 | Guide Part 1: Architecture overview | the request path; EC2 vs ECS vs Fargate; who manages what |
| 2 | Part 7: Networking and security | VPC tiers, security-group chain, NAT vs VPC endpoints, IAM roles, OIDC |
| 3 | Part 2: Inventory of files | what each file defines, who reads it, where a setting belongs |
| 4 | Part 5: YAML, JSON, HCL and shell | HCL ≠ YAML; who reads which format and when it takes effect |
| 5 | Part 6: React hosting | private S3 + CloudFront OAC, SPA fallback, cache headers |
| 6 | Part 4 (Fargate) **or** Part 3 (EC2) walkthrough | every command, expected output and failure case |
| 7 | Part 9: Operational diagrams | first deploy, update, rollback, scaling, DB failover, CI/CD |
| 8 | Part 8: Scaling | users → RPS → per-task capacity → fleet size; DB connection budget |
| 9 | Part 10: Comparison and checklist | the recommendation, cleanup and ongoing charges |

Tip: open the Appendix next to the guide. Every excerpt in the guide is cut from a complete file there.

---

## To-do: deploying (ECS Fargate path)

Tick the boxes as you go. Step numbers refer to guide Part 4 (and Part 3 for the shared bootstrap).

### Phase 0: Prepare
- [ ] Read learning-path items 1–6
- [ ] AWS account with an admin/SSO login; AWS Budget alert configured
- [ ] Domain with a Route 53 public hosted zone
- [ ] Tools installed: AWS CLI v2, Terraform ≥ 1.10, Docker, Session Manager plugin, `jq`
- [ ] Replace every `REPLACE` placeholder (account ID `111122223333`, `example.com`, `OWNER/ai-engineering-lab`)
- [ ] Decide where the React app lives (the workflows expect `frontend/` with `npm run build` → `dist/`)

### Phase 1: Bootstrap (one time)
- [ ] Create the Terraform state bucket (versioned, public access blocked)
- [ ] Create Secrets Manager secret `ai-lab/prod/api` (CLI, never Terraform)
- [ ] `cp terraform.tfvars.example terraform.tfvars` → edit → `terraform init` → `terraform validate`

### Phase 2: Image
- [ ] `docker build` the API image locally
- [ ] `terraform apply -target=aws_ecr_repository.api -target=aws_ecr_lifecycle_policy.api`
- [ ] Push the image tagged with the git SHA; set `initial_image_tag`

### Phase 3: Infrastructure
- [ ] `terraform plan -out=tfplan` and review every resource
- [ ] `terraform apply tfplan`
- [ ] Service shows `COMPLETED`; targets healthy in 2 AZs
- [ ] `https://api.example.com/actuator/health` returns UP; HTTP redirects to HTTPS

### Phase 4: Frontend
- [ ] Build React, `aws s3 sync` to the frontend bucket, invalidate `/index.html`
- [ ] `https://app.example.com/some/deep/link` returns 200 (SPA fallback)
- [ ] API allows CORS from `https://app.example.com`

### Phase 5: CI/CD
- [ ] Create GitHub environment `production`; set variables from `terraform output`
- [ ] `git mv infrastructure/aws/github-workflows/deploy-ecs.yml .github/workflows/`
- [ ] A push to `main` deploys; a deliberately broken image rolls back automatically

### Phase 6: Operate
- [ ] Confirm the SNS alarm e-mail subscription; alarms in `OK`
- [ ] Kill a task and watch it self-heal
- [ ] Load test; replace the illustrative min/max/targets with measured values
- [ ] Check the DB connection budget (`SHOW max_connections`)
- [ ] Test failover (`aws rds reboot-db-instance --force-failover`) and a point-in-time restore
- [ ] Plan for RDS secret rotation (redeploy after rotation, or use a longer schedule)
- [ ] Walk through the final checklist (guide 10.5)

### When finished experimenting
- [ ] Follow guide 10.4 (cleanup). NAT gateway, ALB and RDS keep charging until they are destroyed

---

## Notes
- **EC2 path instead?** Use guide Part 3, `infrastructure/aws/ec2/` and `deploy-ec2.yml`. The bootstrap uploads a jar
  instead of pushing an image.
- **Local development** stays on the repository's `docker-compose.yml`. Compose is not used in AWS.
- **Never commit** Terraform state, `terraform.tfvars` or saved plans (now covered by `.gitignore`).
  **Do commit** `.terraform.lock.hcl` after your first `terraform init`.
